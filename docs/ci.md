# CI

One workflow, `.github/workflows/ci.yml`, three jobs: `lint → android → ios`, each gated on the
previous via `needs:`. Worth explaining why that's sequential, since a production pipeline for
this exact project would very likely run `android` and `ios` in parallel instead.

## Why sequential here

- **Cheapest, fastest check first.** `lint` runs ktlint (Kotlin) and SwiftLint (App/) on
  `ubuntu-latest` — no simulator, no emulator, done in well under a minute. There's no reason to
  spend a macOS runner's time compiling anything if the code doesn't even pass formatting/style
  checks; `android` and `ios` both wait on it.
- **macOS runners are the expensive, slow part.** GitHub-hosted macOS minutes cost several times
  what Linux minutes do against a free/personal account's included quota, and `ios` (XCFramework
  build + `xcodegen generate` + `fastlane test`) is by a wide margin the slowest job here. Gating
  it behind `android` means a shared-module regression that breaks the Android build (which also
  exercises `:shared:jvmTest`, i.e. most of the same Kotlin the iOS side depends on) fails fast on
  a cheap Linux runner instead of burning macOS minutes to discover the same thing later.
- **This is a demo repo with one contributor at a time**, not a team where two engineers are
  pushing to `android/` and `ios/` in the same hour and want independent fast feedback. That's the
  actual case *for* running them in parallel, and it doesn't apply here.

## Where this would change for a real team/production pipeline

Parallelize `android` and `ios` (both only depend on `lint`, not on each other) once:

- multiple people are landing platform-specific changes concurrently and want independent signal,
  and/or
- the `shared/` module is stable enough that a break there is rare rather than the common case
  this project is still iterating through.

The fix is mechanical — drop `android` from `ios`'s `needs:` list — which is exactly why it's
worth leaving as a documented, deliberate choice rather than something that looks like an
oversight.

## What's not in this pipeline

- No signing, no publishing, no crash reporting upload — `RELEASE.md` covers what each of those
  needs and why none of it is wired up against a repo with no App Store Connect / Play Console
  access.
- No `jvmTest --tests "*LiveTest"` (the live-integration suite hitting real Binance/Sepolia
  endpoints) — see `docs/testing.md`. Running it in CI would make every PR's status depend on
  third-party service uptime rather than on this repo's own code, which is the wrong dependency
  for a required check to have. Run it locally before touching `EthereumRpcClient`,
  `TokenRepository`, or `OrderBookClient`.
- No Android instrumented/UI test job — there isn't an instrumented UI suite yet (`docs/testing.md`
  covers what verification exists today: manual `uiautomator`/Compose Preview checks).
