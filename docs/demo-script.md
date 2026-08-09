# Demo script

A walkthrough for showing this project live, in an order that front-loads the parts that prove
the architecture actually holds up, not just that the screens exist.

Run on iOS Simulator or an Android emulator — see the README's Quick Start for build commands.

## 1. Prices tab (Swift) and KMP tab — the reconnect story

Both tabs show the same live Binance trade stream, one from the standalone `RealtimeFeed` Swift
package, one from the shared KMP module. They're deliberately kept side by side so the difference
between "hand-written Swift" and "KMP-generated Swift API" is visible directly, not just claimed.

To show the reconnect behavior actually works, not just that it's implemented:

1. Open either tab, let a few ticks come in.
2. Turn on Airplane Mode (Simulator: toggle Wi-Fi off in System Settings; emulator: toggle
   Airplane Mode from the extended controls). The connection state indicator should flip to
   reconnecting.
3. Turn it back on. Ticks should resume within a few seconds, with backoff timing visible if you
   toggle it off and on a few times in a row (each retry waits longer than the last).

The order-book variant (if wired to a visible screen at demo time) additionally recovers from
sequence gaps rather than silently corrupting state — that's the part of `docs/architecture.md`
worth narrating here: raw `@depth` stream + REST snapshot + gap detection, not the throttled
`@depth@100ms` stream that would hide the problem.

## 2. Wallet tab — hardware-backed signing

This is intentionally unrelated to the WalletConnect flow in tab 4 — say so up front, since the
name overlap invites confusion.

1. Tap "Generate Key." This creates a Secure Enclave (iOS) / Keystore+StrongBox (Android)
   P-256 key pair — the private half never leaves hardware.
2. Tap "Sign." The OS biometric prompt should appear (Face ID / fingerprint) — this is the actual
   proof point: the signing operation is gated by hardware, not by an app-level password check.
3. Point out the signature output and, if asked, the code path in `WalletKeyStore` — worth having
   open in an editor, since "where does the key live" is the natural follow-up question.

On Simulator without biometric enrollment, there's a software Keychain fallback with the same
access control semantics — mention this is a Simulator limitation, not app behavior, if it comes
up.

## 3. Portfolio tab — WalletConnect + real chain reads

The highest-stakes part of the demo, since it depends on a live relay round-trip and a real RPC
call, not local state.

1. Tap "Connect Wallet." The official Reown/WalletConnect modal should present — this is the
   piece that took the most engineering effort (real SDK, real project ID, correct init ordering
   on both platforms) and is worth calling out explicitly: this app never sees or requests a seed
   phrase at any point in this flow.
2. If a wallet app (e.g. MetaMask Mobile, set to Sepolia) is available to actually pair with,
   scan/approve the connection. If not, the modal presenting correctly is itself the demonstrable
   part — proceeding to pair requires a second device or app.
3. Once connected (or using a pre-connected session for time), the LINK balance shown comes from
   a live `eth_call` against `ethereum-sepolia-rpc.publicnode.com` — refresh to show it's a real
   network call, not a mock, if there's any doubt.
4. If asked about the exact-integer-math point from the README: the raw `uint256` returned by the
   RPC call is decoded into a `BigInteger` (`TokenAmount`), never a `Double`, before it's formatted
   for display — worth showing `TokenAmountTest.kt` if the conversation goes there.

## Fallback notes

- Sepolia public RPC and the WalletConnect relay are both third-party services outside this
  project's control — if either is down or slow during a live demo, that's a real-world
  reliability example to talk through (the README's error model / `docs/architecture.md` cover
  how failures surface to the UI rather than crashing), not a project bug.
- iOS Simulator's network stack has occasional DNS flakiness independent of this app (seen during
  development, documented rather than "fixed" since it isn't within the app's control) — if a
  request hangs, background/foreground the Simulator once rather than assuming the code is wrong.
