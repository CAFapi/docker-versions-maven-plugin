#### Version Number
${version-number}

#### New Features
- None

#### Bug Fixes
- **I941175**: Fixed the project registry name sanitization.

#### Known Issues
- None

#### Breaking Changes
- **I941175**: Project registry name sanitization has moved to the plugin extension instead of the goal. The plugin no longer reads the `projectDockerRegistry` property, it only sets it.
If the extension is used and the `projectDockerRegistry` property is set an exception will be thrown.
