# Contributing

This is a demo project, not a product with a review queue — but if you're picking it up (including
future-me), here's how it's organized and what to check before a change is "done."

## Where things go

- Logic that both platforms need (or that's independently testable without a socket/HTTP/SDK in
  the loop) goes in `shared/src/commonMain`. If it needs a platform API, it doesn't belong there —
  see `docs/architecture.md` for the module map and the dependency rule.
- Anything wallet-adjacent (hardware key signing, WalletConnect) stays platform-native by design —
  ADR-003 in `docs/adr/` explains why, and the README's "design decisions" section covers the
  Secure Enclave / Keystore split specifically.
- New ADRs go in `docs/adr/`, numbered sequentially, using the existing four as the template
  (Status/Context/Decision/Alternatives/Consequences/Revisit triggers).

## Before opening a change

1. **Shared logic changes**: add a `commonTest` case first if the change is behavioral, not just
   a signature change. `./gradlew :shared:jvmTest` runs fast and needs no simulator/emulator —
   run it before anything platform-specific.
2. **RPC/ABI/repository changes**: there's a `jvmTest` live-integration test suite that hits real
   endpoints (Binance, Sepolia RPC). It's slower and network-dependent, which is why it's separate
   from the offline `commonTest` suite — run it if you touched `EthereumRpcClient`, `Abi`, or
   either repository.
3. **Platform UI changes**: build and actually run the affected app. `docs/testing.md` has the
   commands; there's no headless UI test runner here, XCUITest and manual Compose Preview /
   emulator checks are what exist.
4. **New Gradle/SPM dependency**: check whether it needs to be commonMain (shared, both platforms)
   or genuinely platform-specific first — see the README's note on why `WalletGateway` isn't a
   shared Kotlin type despite the temptation.

## Style

- No comments explaining *what* code does — names should cover that. Comments are for *why*: a
  workaround for a specific SDK bug, a non-obvious ordering requirement (there are a few — see
  `Web3DemoApp.swift`'s `configureAppKit()` for `Networking.configure` before `AppKit.configure`),
  a constraint that isn't visible from the code alone.
- Don't add abstraction for a single call site. Three real duplicated call sites, not "might need
  it later," is the bar — same reasoning as `docs/architecture.md`'s take on why there's no
  generic repository base class.
- Kotlin: ktlint, enforced in CI (`./gradlew ktlintCheck`, or `ktlintFormat` to auto-fix). Compose
  functions are exempted from the function-naming rule via `.editorconfig` — see the comment
  there for why.
- Swift: SwiftLint, enforced in CI (`swiftlint lint --strict` from `App/`). A couple of
  `// swiftlint:disable:next` uses exist for cases where the flagged pattern is actually correct
  (see the comments next to them in `WalletKeyStore.swift` and `Web3DemoApp.swift`) — don't add
  more without the same kind of justification.

## Commit scope

Keep changes to one concern — a repository change and a UI change that consumes it can be one
commit if they're genuinely coupled, but "also reformatted this file" or "also bumped this
unrelated dependency" should be its own commit.
