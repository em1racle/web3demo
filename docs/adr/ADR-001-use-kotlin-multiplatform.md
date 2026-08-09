# ADR-001: Use Kotlin Multiplatform for shared business logic

## Status

Accepted

## Context

The role requires both iOS (Swift) and Android (Kotlin) depth from one engineer, on an app whose
hardest problems — realtime reconnect/backpressure, RPC/ABI encoding, token-refresh timing — are
identical in shape on both platforms. Writing and maintaining that logic twice, in two languages,
means every bug fix and every edge case has to be found and fixed twice, and the two
implementations will drift over time even with the best intentions.

## Decision

Business logic (realtime clients, reconnect/backoff policy, order-book sequencing, RPC/ABI
codec, token/NFT repositories) lives once, in `shared/commonMain`, compiled to a JVM artifact for
Android and an XCFramework for iOS. UI stays fully native — SwiftUI on iOS, Jetpack Compose on
Android — and platform-only concerns (Secure Enclave, Keystore, BiometricPrompt) stay native too,
never forced through the shared layer.

## Alternatives considered

- **Two separate native codebases.** Simplest mental model, but doubles the surface area for the
  hardest bugs in this project (reconnect timing, sequence-gap detection) and doesn't answer the
  actual question this demo exists to answer.
- **React Native / Flutter (shared UI too).** Rejected outright — the JD explicitly wants native
  UI depth on both platforms (SwiftUI re-render behavior, Compose recomposition), which a
  cross-platform UI layer would hide rather than demonstrate.
- **Shared UI logic via KMP + Compose Multiplatform for iOS.** Immature enough on iOS (relative to
  SwiftUI's own tooling) that it would trade a proven risk (write logic twice) for a less-proven
  one (iOS UI on a rendering stack built primarily for Android).

## Consequences

- Two build toolchains (Gradle + Xcode) instead of one; Kotlin/Native's Xcode-version
  compatibility lags real Xcode releases, which is a live source of yellow-flag warnings.
- `StateFlow` doesn't reach Swift as `AsyncSequence` without an extra library — a small callback
  bridge (`PriceFeedController`) exists purely for that, adding one indirection layer on the iOS
  side that Android doesn't need.
- Any bug found in `shared/` is fixed once for both platforms — this already paid off during
  development (see `docs/review.md` / commit history for the order-book buffering bug and the
  auth-token cancellation-ordering bug, both caught once and fixed for both platforms
  simultaneously).

## Revisit triggers

- If Compose Multiplatform's iOS support matures to the point that native SwiftUI stops being a
  meaningfully better default for iOS UI depth signaling.
- If the team splits into dedicated iOS/Android engineers — the cross-language maintenance cost
  of KMP is only worth paying when one person (or a very small team) owns both platforms.
