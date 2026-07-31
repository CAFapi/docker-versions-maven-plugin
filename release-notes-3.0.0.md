!not-ready-for-release!

#### Version Number
${version-number}

#### Breaking Changes
- **D1200007**: An image pull request will only time out if no response is received from the Docker Daemon within the configured RESPONSE_TIMEOUT_SECONDS.
  * DOWNLOAD_IMAGE_TIMEOUT_SECONDS : No longer used. Where it is present in the plugin cfg, downloadImageTimout should be removed.
  * RESPONSE_TIMEOUT_SECONDS : default increased to 300 seconds. 

#### New Features
- **US984062**: Added a new `skipPull` config param to skip pulling an image before retagging it. This could be used when working with developer images.

#### Known Issues
- None
