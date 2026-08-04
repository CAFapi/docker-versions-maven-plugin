/*
 * Copyright 2024-2026 Open Text.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.cafapi.docker_versions.plugins.test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cafapi.docker_versions.docker.client.PullProgressCallback;
import com.github.dockerjava.api.model.PullResponseItem;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

final class PullProgressCallbackTest
{
    private static final Logger LOGGER = LoggerFactory.getLogger(PullProgressCallbackTest.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ch.qos.logback.classic.Logger callbackLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void init(final TestInfo testInfo)
    {
        LOGGER.info("Running test: {}...", testInfo.getDisplayName());

        callbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PullProgressCallback.class);
        appender = new ListAppender<>();
        appender.start();
        callbackLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown()
    {
        callbackLogger.detachAppender(appender);
    }

    /**
     * Regression test for a NullPointerException that used to be thrown when the blended, cross-layer download
     * percentage crossed a new 10% threshold on a progress event for a layer that had not itself reported a
     * "total" size yet. Registries can omit the total on a layer's earliest progress events (e.g. when using
     * chunked transfer-encoding), and {@code ResponseItem.ProgressDetail#getTotal()} is {@code @CheckForNull},
     * so a lookup of that layer's own recorded total must tolerate a missing entry instead of unboxing a null.
     */
    @Test
    void testDownloadingProgressDoesNotThrowWhenItemTotalUnknown() throws Exception
    {
        final PullProgressCallback callback = new PullProgressCallback("library/test", "latest");

        assertDoesNotThrow(() -> {
            callback.onNext(item("{\"id\":\"layerA\",\"status\":\"Downloading\","
                + "\"progressDetail\":{\"current\":100,\"total\":1000}}"));
            callback.onNext(item("{\"id\":\"layerB\",\"status\":\"Downloading\","
                + "\"progressDetail\":{\"current\":50}}"));
            callback.onNext(item("{\"id\":\"layerA\",\"status\":\"Downloading\","
                + "\"progressDetail\":{\"current\":400,\"total\":1000}}"));
            // Before the fix, this event crossed a new 10% threshold while "layerB" had never reported its own
            // total, causing a NullPointerException that aborted an otherwise healthy pull.
            callback.onNext(item("{\"id\":\"layerB\",\"status\":\"Downloading\","
                + "\"progressDetail\":{\"current\":300}}"));
        });
    }

    @Test
    void testAlreadyExistsIsLoggedOncePerLayer() throws Exception
    {
        final PullProgressCallback callback = new PullProgressCallback("library/test", "latest");

        callback.onNext(item("{\"id\":\"layerA\",\"status\":\"Already exists\"}"));
        callback.onNext(item("{\"id\":\"layerA\",\"status\":\"Already exists\"}"));

        final long count = loggedMessages().stream().filter(m -> m.equals("Already exists: layerA")).count();
        assertEquals(1, count, "Expected 'Already exists' to be logged exactly once per layer, got: " + loggedMessages());
    }

    @Test
    void testPullCompleteIsLoggedPerLayer() throws Exception
    {
        final PullProgressCallback callback = new PullProgressCallback("library/test", "latest");

        callback.onNext(item("{\"id\":\"layerA\",\"status\":\"Downloading\","
            + "\"progressDetail\":{\"current\":1000,\"total\":1000}}"));
        callback.onNext(item("{\"id\":\"layerA\",\"status\":\"Pull complete\"}"));

        assertTrue(loggedMessages().stream().anyMatch(m -> m.startsWith("Pull complete: layerA")),
            "Expected a 'Pull complete' log entry for layerA, got: " + loggedMessages());
    }

    @Test
    void testOnCompleteLogsFinalSuccessMessage()
    {
        final PullProgressCallback callback = new PullProgressCallback("library/test", "latest");

        callback.onComplete();

        assertTrue(loggedMessages().contains("Successfully pulled library/test:latest"),
            "Expected a final success log message, got: " + loggedMessages());
    }

    /**
     * Regression test for a percentage that visually regressed in the log when a newly discovered layer (with a
     * much larger size) dragged the blended, cross-layer percentage back down below what had already been
     * logged. The callback should hold off on logging rather than emit a lower percentage than before.
     */
    @Test
    void testBlendedPercentageDoesNotRegressWhenLargerLayerIsDiscovered() throws Exception
    {
        final PullProgressCallback callback = new PullProgressCallback("library/test", "latest");

        callback.onNext(item("{\"id\":\"layerA\",\"status\":\"Downloading\","
            + "\"progressDetail\":{\"current\":900,\"total\":1000}}"));
        callback.onNext(item("{\"id\":\"layerB\",\"status\":\"Downloading\","
            + "\"progressDetail\":{\"current\":10,\"total\":9000}}"));

        final List<String> percentLogs = loggedMessages().stream()
            .filter(m -> m.contains("% of"))
            .collect(Collectors.toList());

        assertEquals(1, percentLogs.size(), "Expected no regressed percentage log entry, got: " + percentLogs);
        assertTrue(percentLogs.get(0).contains("90%"), "Expected the logged percentage to be 90%, got: " + percentLogs.get(0));
    }

    private static PullResponseItem item(final String json) throws Exception
    {
        return MAPPER.readValue(json, PullResponseItem.class);
    }

    private List<String> loggedMessages()
    {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }
}
