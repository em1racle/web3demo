fastlane documentation
----

# Installation

Make sure you have the latest version of the Xcode command line tools installed:

```sh
xcode-select --install
```

For _fastlane_ installation instructions, see [Installing _fastlane_](https://docs.fastlane.tools/#installing-fastlane)

# Available Actions

## iOS

### ios test

```sh
[bundle exec] fastlane ios test
```

Run unit + UI tests on the iOS Simulator — no signing needed, runs as-is

### ios build_simulator

```sh
[bundle exec] fastlane ios build_simulator
```

Build a Debug .app for the simulator — no signing needed, runs as-is

### ios beta

```sh
[bundle exec] fastlane ios beta
```

Build, sign, and upload a TestFlight build. NOT wired to real credentials — needs match (or manual signing) plus an App Store Connect API key configured as CI secrets. See RELEASE.md.

----

This README.md is auto-generated and will be re-generated every time [_fastlane_](https://fastlane.tools) is run.

More information about _fastlane_ can be found on [fastlane.tools](https://fastlane.tools).

The documentation of _fastlane_ can be found on [docs.fastlane.tools](https://docs.fastlane.tools).
