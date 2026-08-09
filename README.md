# web3demo

Realtime market data (Binance) shared between a native iOS app and a native Android app via
Kotlin Multiplatform, plus a hardware-backed wallet signing screen on each platform. Built to
check whether "shared business logic, native UI per platform" actually holds up in practice
before committing to it as an architecture — not a toy CRUD app.

There's also a standalone Swift package (`RealtimeFeed/`) that does the same realtime logic with
zero Kotlin involved, kept around as a second reference implementation.

## Layout

```
RealtimeFeed/   Swift package — WebSocket client, reconnect/backpressure, no KMP
shared/         Kotlin Multiplatform module (JVM + Android + iOS) — the actual shared logic
App/            iOS app (SwiftUI), consumes both RealtimeFeed and shared/ side by side
androidApp/     Android app (Compose), consumes shared/
```

## Building

### shared/

```
./gradlew :shared:jvmTest                    # unit tests, no network
./gradlew :shared:compileDebugKotlinAndroid  # android target
./gradlew :shared:assembleSharedXCFramework  # ios target -> shared/build/XCFrameworks
```

Needs a JDK 17 on `JAVA_HOME` and an Android SDK at `local.properties` (`sdk.dir=...`).

### iOS app

```
cd App
xcodegen generate
xcodebuild -project Web3Demo.xcodeproj -scheme Web3Demo \
  -destination 'platform=iOS Simulator,name=iPhone 17' build
```

`App/Web3Demo.xcodeproj` is generated, not checked in — regenerate it after pulling if
`project.yml` changed. Needs the XCFramework built first (see above).

### Android app

```
./gradlew :androidApp:assembleDebug
```

### RealtimeFeed (standalone)

```
cd RealtimeFeed
swift test
swift run RealtimeFeedDemo   # prints live BTC/ETH/SOL prices to stdout
```

## What's tested where

Pure logic (backoff timing, order-book sequencing, token-refresh timing) is separated from the
actual socket/HTTP code specifically so it can be unit tested without a network — see
`ReconnectPolicy`, `OrderBookSync`, `AuthTokenGate` in `shared/src/commonMain`. Everything else
either has a live integration test against the real Binance API (`OrderBookClientLiveTest`) or a
UI test that drives real taps (`WalletUITests`).

## A few design decisions worth knowing before reading the code

- **`PriceFeedController` exists only for Swift.** Kotlin `StateFlow` doesn't bridge to Swift's
  `AsyncSequence` without an extra library (SKIE / KMP-NativeCoroutines), so there's a thin
  callback-based wrapper around the Flow-based client just for the iOS side. Android talks to the
  Flow-based client directly.
- **`KeyValueStore` is a plain interface, not `expect`/`actual`.** Android's implementation needs
  a `Context` in its constructor; iOS's doesn't need anything. `expect`/`actual` classes require
  matching constructors across platforms, so a plain interface (exported as a real Swift protocol
  in the XCFramework) fits better here.
- **Wallet code is not shared.** Secure Enclave (iOS) and Keystore/StrongBox (Android) are
  deliberately platform-native, in `App/Sources/WalletKeyStore.swift` and
  `androidApp/.../WalletKeyStore.kt` respectively, not in `shared/`.
- **Order book uses the raw `@depth` stream, not `@depth@100ms`.** The throttled variant would
  hide the backpressure problem it's meant to demonstrate.
- **Subscriptions go in the WebSocket URL, not a control frame.** Sending a control frame right
  after `resume()` races the handshake on `URLSessionWebSocketTask`. Binance supports encoding
  subscriptions directly in the connection URL, which sidesteps the race and makes
  resubscribe-on-reconnect trivial (reconnecting to the same URL just is resubscribing).

## Known gaps

- The wallet screens generate a hardware-backed keypair and sign an arbitrary message. That's the
  credential-custody mechanics of a real wallet, not a real wallet — no address derivation, no
  transaction encoding, nothing gets broadcast anywhere, and the curve (P-256) isn't the one
  BTC/ETH actually use (secp256k1). See the comments in `WalletKeyStore` for what a real
  implementation would need to change.
- CI (`.github/workflows/`) builds and tests only. It doesn't sign, publish, or report crashes —
  see `RELEASE.md` for what that would take and why it isn't wired up here.
