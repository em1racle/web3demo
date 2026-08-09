# Testing

The guiding rule (also in the README): pure logic is kept separate from socket/HTTP/SDK code
specifically so it's unit-testable without a network. That split is what makes most of this
suite fast and deterministic; the rest is deliberately live and slower, and kept in a separate
source set so it doesn't run by accident.

## commonTest — offline, deterministic, runs everywhere

```bash
./gradlew :shared:jvmTest
```

No network, no simulator/emulator. This is the suite to run on every change to `shared/`.

- `realtimefeed/ReconnectPolicyTest.kt` — backoff timing and jitter bounds
- `realtimefeed/OrderBookSyncTest.kt` — sequence-gap detection against the order book, including
  the actual Binance local-order-book reconciliation algorithm (first-event/prev-final-update-ID
  checks), independent of any real socket
- `realtimefeed/PersistedPriceCacheTest.kt` — cache read/write against a fake `KeyValueStore`
- `realtimefeed/AuthTokenGateTest.kt`, `AuthenticatedConnectionGateTest.kt` — token refresh timing
  and the "proactive refresh watcher vs. normal connect return" race (this one caught a real bug
  during development — see the fix note in the ADRs/commit history if curious)
- `chain/AbiTest.kt` — ERC-20/ERC-721 function selector encoding, `uint256`/`address`/`string`
  decode round-trips
- `chain/TokenAmountTest.kt` — `BigInteger`-backed exact arithmetic and decimal formatting
- `chain/IpfsGatewayTest.kt` — `ipfs://` URI normalization and unsafe-scheme rejection
- `chain/NftRepositoryTest.kt` — metadata parsing against a mocked HTTP engine (Ktor `MockEngine`)

`NftRepositoryTest` uses `runBlocking`, not `runTest`, even though it's otherwise a commonTest —
`runTest`'s virtual time doesn't play well with `MockEngine`'s real (if fake-backed) async
dispatch and produced spurious timeouts during development. Everything else in this suite that
doesn't need real async I/O uses `runTest` as normal.

## jvmTest — live integration, real network, slower

```bash
./gradlew :shared:jvmTest --tests "dev.web3demo.*.*LiveTest"
```

Hits real endpoints. Not run in CI (see `docs/ci.md` for why) — run locally when touching
`EthereumRpcClient`, `TokenRepository`, `OrderBookClient`, or anything else that talks to a real
service.

- `realtimefeed/OrderBookClientLiveTest.kt` — connects to the actual Binance combined stream
- `chain/TokenRepositoryLiveTest.kt` — reads a real Sepolia LINK balance via
  `ethereum-sepolia-rpc.publicnode.com`

Both are network-dependent by design — flaky only if Binance or the public RPC endpoint is
actually down, which is itself useful signal.

## RealtimeFeed (standalone Swift package)

```bash
cd RealtimeFeed && swift test
```

`Tests/RealtimeFeedTests/ReconnectPolicyTests.swift` — the same backoff logic as the Kotlin
`ReconnectPolicyTest`, kept as an independent implementation on purpose (see the README for why
this package exists at all: proving the architecture without KMP as a fallback comparison).

## iOS UI tests

```bash
cd App && bundle exec fastlane test
```

or directly:

```bash
xcodebuild test -project Web3Demo.xcodeproj -scheme Web3Demo \
  -destination 'platform=iOS Simulator,name=iPhone 17'
```

- `UITests/WalletUITests.swift` — drives the hardware-key generate/sign flow, asserts the
  biometric-fallback path completes
- `UITests/PortfolioUITests.swift` — taps "Connect Wallet" and asserts the real Reown/WalletConnect
  modal actually presents (`app.staticTexts["Connect wallet"].exists`), with a screenshot attached
  to the test result either way. This is a live-network test — it depends on the WalletConnect
  relay being reachable — by design, since a mocked version wouldn't prove the SDK integration
  actually works.

There's no equivalent for the Prices/KMP tabs — reconnect behavior is exercised via `commonTest`
(`ReconnectPolicyTest`), and driving actual network loss from an XCUITest would need device-level
network control XCUITest doesn't expose cleanly; the demo script (`docs/demo-script.md`) covers
manual verification instead.

## Android

No dedicated instrumented UI test suite yet — verified manually during development via
`adb shell screencap` + `uiautomator dump` for the WalletConnect modal and Compose Preview for
individual screens. `./gradlew :androidApp:assembleDebug` (and `testDebugUnitTest` if any JVM
tests are added directly to `androidApp/`) is what CI runs; see `docs/ci.md`. Adding a
Compose UI test suite mirroring `PortfolioUITests.swift` is on the roadmap, not done.

## What isn't tested here, on purpose

- WalletConnect *pairing* end-to-end (this app's modal + a second real wallet app approving) isn't
  automatable without a second device/app in the loop — `PortfolioUITests` verifies the modal
  presents, not a full pair. Covered manually in the demo script instead.
- No test signs a real on-chain transaction, because this app never constructs one — see
  `SECURITY.md` and the README's "Known gaps" for why.
