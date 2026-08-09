# Release pipeline — what's wired vs. what's documented

The `test`/`build_simulator` Fastlane lanes and both GitHub Actions workflows run as-is, no
credentials needed — they only build and test. Everything below needs accounts this repo doesn't
have access to, so it's documented rather than half-wired into a build that would just fail.

## iOS

**Signing.** `fastlane match` keeps signing certificates + provisioning profiles in a private git
repo, synced across machines/CI. Needs: an Apple Developer Program membership, a `MATCH_GIT_URL`
secret pointing at that private repo, and `MATCH_PASSWORD` to decrypt it.

**TestFlight upload.** `fastlane beta` (already in `App/fastlane/Fastfile`) needs an App Store
Connect API key (`APP_STORE_CONNECT_API_KEY_ID` / `_ISSUER_ID` / `_KEY` secrets) — generated once
in App Store Connect → Users and Access → Keys, scoped to App Manager.

**Phased release.** App Store Connect supports staged rollout natively per-version (7-day ramp,
1%→100%) — a checkbox at submission time, no extra tooling. Fastlane's `deliver` can toggle it via
`phased_release: true`.

**Guideline 3.1.5(b) (crypto/wallet apps).** Apps offering wallet functionality need to disclose
it in App Review notes and, if the app facilitates trading real assets, may need additional
licensing documentation depending on jurisdiction. Worth flagging explicitly in the review notes
rather than letting a reviewer discover the Wallet tab cold — that's a common rejection trigger.

**Crash reporting.** Sentry or Crashlytics both work; neither is wired in since both need a
project created in an account this repo doesn't have. Crashlytics additionally needs
`GoogleService-Info.plist` from a Firebase project.

## Android

**Signing.** A release keystore (`.jks`) doesn't exist in this repo (and shouldn't be committed
un-encrypted). Real setup: generate one, store it + its passwords as CI secrets, reference via a
`signingConfigs { release { ... } }` block in `androidApp/build.gradle.kts` populated from
`System.getenv(...)`, not hardcoded.

**Play Console publishing.** The `com.github.triplet.play` Gradle plugin automates uploads given a
service account JSON (Play Console → Setup → API access → create service account, grant Release
Manager permissions). Not applied in `androidApp/build.gradle.kts` here — applying it without that
JSON would just break local builds.

**Staged rollout.** Same plugin supports `track.set("production")` + `userFraction` for a
percentage rollout, mirroring the App Store Connect phased release above.

**Financial Services / crypto-exchange declaration.** Play Console has a specific declaration
form for apps handling cryptocurrency (Policy → App content → Financial features). Same reasoning
as the App Store note above: the Wallet tab needs to be disclosed there, not discovered.

**Crash reporting.** Firebase Crashlytics needs `google-services.json` from a Firebase project
(same project as iOS's `GoogleService-Info.plist`, ideally) plus the `google-services` and
`firebase-crashlytics` Gradle plugins — not applied here for the same "don't break local builds
over missing config" reason as everything else on this list.
