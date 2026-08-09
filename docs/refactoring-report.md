# Refactoring report

Applies the findings from `docs/review.md`. Each change is scoped to exactly the finding it
fixes — no incidental cleanup bundled in. Verified after each change: `./gradlew ktlintCheck
:shared:jvmTest :androidApp:assembleDebug`, plus a full iOS rebuild
(`:shared:assembleSharedDebugXCFramework` → `xcodegen generate` → `xcodebuild build`) and
`swiftlint lint --strict`. The live `TokenRepositoryLiveTest` suite (real Sepolia RPC calls) was
re-run against the rewritten `TokenRepository` specifically, not just the offline suite.

## Fixed

### #1 (HIGH) — malformed RPC responses crashing instead of surfacing as `AppError`

**Before:** `TokenRepository`/`NftRepository` called `Abi.decodeUint256`/`decodeUint8`/
`decodeString`/`decodeAddress` directly on RPC results, with no try/catch — a malformed `result`
field threw an uncaught exception, which is a fatal, uncatchable crash once it crosses into Swift
(no `@Throws` annotation on these suspend functions).

**After:** added `AppResult<String>.decoded(transform)` (`chain/AppError.kt`) — the same shape as
the existing `.map()`, but catches the transform and converts a failure into
`AppResult.Err(AppError.InvalidData(...))`. `NftRepository.fetchOwner`/`fetchTokenUri` now use
`.decoded(...)` instead of `.map(...)`. `TokenRepository.fetchBalance` does the same.
`TokenRepository.fetchMetadata`'s three decode calls are wrapped in a single try/catch (see #8
below — this method was rewritten anyway for concurrency, so the fix landed in the same place).

**Debt removed:** the one call path most likely to see genuinely malformed input (an
unauthenticated, non-pinned public RPC endpoint per `SECURITY.md`) no longer has a crash as its
failure mode.

### #2 (MEDIUM) — Android wallet-request params built by raw string interpolation

**Before:** `ReownWalletGateway.kt`'s `signMessage`/`sendTransaction` spliced `message`/`to`/
`valueWei`/`data` into JSON string templates directly, with no escaping.

**After:** both now build params with `kotlinx.serialization`'s `buildJsonArray`/`addJsonObject`/
`JsonPrimitive`, the same library already used throughout `EthereumRpcClient`. Required adding
`kotlinx-serialization-json` (and the `kotlin("plugin.serialization")` plugin) as a direct
`androidApp` dependency — it was previously only reachable through `shared`, which doesn't expose
it as `api`, so `androidApp` couldn't actually import it before this.

**Debt removed:** a message or address containing `"` or `\` no longer produces malformed or
shifted JSON-RPC params. Confirmed the module now compiles and resolves the import
(`./gradlew :androidApp:compileDebugKotlin`) — no behavioral test added since neither method has a
caller yet (see Open questions in `docs/review.md`).

### #3 (MEDIUM) — `EthereumRpcClient`'s default `HttpClient` recreated on every session change

**Before:** both `PortfolioScreen.kt` (Android) and `PortfolioView.swift` (iOS) constructed a new
`TokenRepository(EthereumRpcClient())` inside the effect keyed on wallet session, so every
connect/disconnect leaked one HTTP client's engine/connection pool.

**After:**
- Android: hoisted to `val repository = remember { TokenRepository(EthereumRpcClient()) }` above
  the `LaunchedEffect`, so Compose constructs it once per composition, not once per session
  transition.
- iOS: this needed more than moving the `let` up — a plain stored property on a SwiftUI `View`
  struct is reconstructed every time the struct itself is (i.e. on every re-render, not just
  session changes), which would have been a regression. Added a small
  `@MainActor final class PortfolioServices: ObservableObject` holding the one `TokenRepository`,
  and a `@StateObject private var services = PortfolioServices()` — `@StateObject` is what
  actually survives SwiftUI's struct re-creation.

**Debt removed:** one client per screen lifetime on both platforms, not one per connect/disconnect
cycle.

### #4 (MEDIUM) — `shared`'s Android target used `Dispatchers.Main` via an undeclared transitive dependency

**Before:** `kotlinx-coroutines-android` (required by `PriceFeedController`'s `Dispatchers.Main`)
wasn't declared anywhere in `shared/build.gradle.kts`; it worked only because `androidApp`'s
Compose/AndroidX/Reown dependencies happened to pull it in transitively.

**After:** added `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")`
directly to `shared/build.gradle.kts`'s `androidMain` source set.

**Debt removed:** `shared`'s actual runtime requirement is now declared where it's used, not
implied by whatever else happens to be on a consuming app's classpath.

### #5 (LOW) — iOS `sendRequest` didn't handle `Task` cancellation

**Before:** `ReownWalletGateway.swift`'s `sendRequest` used a bare `withCheckedThrowingContinuation`
with no cancellation handling — a cancelled `Task` left the Combine subscription and the
suspended continuation alive indefinitely.

**After:** wrapped in `withTaskCancellationHandler`, with a small `PendingResponseBox` (lock-guarded,
`@unchecked Sendable`) holding the continuation and subscription so either the wallet's response
or a cancellation can resume exactly once and tear down the subscription. A lock was necessary
rather than actor isolation because `onCancel` runs synchronously and isn't guaranteed to be on
the `MainActor`, so it can't `await` its way onto one.

**Debt intentionally left:** the box doesn't handle the (very narrow) case of the task already
being cancelled before the Combine subscription is set up — `onCancel` firing before
`setPending()` is called would still leave the continuation unresumed. Closing that fully needs a
"cancelled-before-start" flag checked inside `setPending`, which felt like real added complexity
for a race that requires cancellation to land in a sub-millisecond window right at task start, on
a code path nothing currently calls. Worth revisiting if `signMessage`/`sendTransaction` get wired
to UI and cancellation (e.g. a "cancel request" button) becomes a real user action.

### #6 (LOW) — `DefaultCryptoProvider.recoverPubKey` crashed instead of throwing

**Before:** `fatalError(...)`, despite the protocol method being declared `throws`.

**After:** `throw DefaultCryptoProvider.Error.pubKeyRecoveryNotImplemented`, using the throwing
mechanism the method signature already provides.

**Debt removed:** trivial, but real — if this method is ever invoked (SIWE gets enabled, or some
SDK-internal path calls it outside the SIWE flow), the app now surfaces a catchable Swift error
instead of terminating the process.

### #9 (LOW) — `OrderBookClient`'s `Double`-keyed price maps looked like an oversight

**Before:** no comment explaining why `HashMap<Double, Double>` was fine here despite the
project's stated BigInteger-over-Double precedent for token amounts (`TokenAmount.kt`).

**After:** one-line comment on `OrderBookClient.kt`'s `bids`/`asks` fields explaining the
distinction — display-only market prices vs. token amounts that get compared/summed.

**Debt removed:** none functionally; this was purely about a future reader not misreading
consistent, intentional design as an inconsistency.

## Also applied while fixing #1: made `TokenRepository.fetchMetadata` concurrent (finding #8, LOW)

Since #1's fix touched `fetchMetadata` directly, its three sequential RPC round-trips
(name/symbol/decimals) were rewritten to fetch concurrently via `coroutineScope` + `async` +
`await`, each independently — they don't depend on each other. Re-verified against the real
network: `TokenRepositoryLiveTest`'s `fetchesRealMetadataFromSepolia` still passes, now doing three
concurrent calls instead of three sequential ones.

## Intentionally not fixed

### #7 (LOW) — `EthereumRpcClient.nextId` is a non-atomic `var`

Left as-is. As documented in the review, this is currently harmless (nothing correlates responses
back to a request id, so a lost increment can't produce a wrong result) and fixing it properly
means either pulling in an atomics dependency for a single counter or dropping the field
(`nextId`/`id` isn't read back from anywhere) — neither felt justified by the actual risk today.
Revisit if this client ever starts batching requests or matching responses by id.

## Next steps for a real (non-demo) version

None of this changes the fact that this is still a demo, not a production client — the honest
list of what a real version would need beyond these fixes:

- Decide finding #2's open question: either wire `signMessage`/`sendTransaction` to an actual UI
  action, or remove them until something calls them, per the project's own
  "no code for hypothetical requirements" principle.
- A shared "services" container (or platform-idiomatic DI) once more than one screen needs the
  same `TokenRepository`/`EthereumRpcClient` — the `remember`/`@StateObject` fix for #3 is right
  for a single screen but wouldn't be the answer if a second screen needed the same client.
- Close #5's narrow cancellation-race gap if `signMessage`/`sendTransaction` ever get a real
  "cancel this pending request" UI action.
- Everything already listed in `RELEASE.md` (signing, crash reporting, phased rollout) — unrelated
  to this review, still not done, still documented as deliberately not wired up against a repo
  with no App Store Connect/Play Console access.
