# Code review

A pass over the whole codebase (`shared/`, `androidApp/`, `App/`, `RealtimeFeed/`) looking for
correctness, robustness, and consistency issues — not a style pass (ktlint/SwiftLint cover that,
see `docs/ci.md`) and not a re-litigation of anything already called out as a known limitation in
the README, `SECURITY.md`, or the ADRs. Findings are graded by actual current risk: something
unreachable from any real call path is graded lower than the same bug sitting on the Portfolio
tab's live RPC path, even if the code itself looks similar.

Severity scale: **BLOCKER** (breaks the core promise of the demo or is actively dangerous) /
**HIGH** (reachable via normal use, real user-visible failure) / **MEDIUM** (real bug, but needs
an edge case or currently-dead code path to trigger) / **LOW** (correctness nit, consistency gap,
or latent issue with no current trigger).

No BLOCKERs found — nothing here breaks the reconnect/backpressure story, compromises key
material, or contradicts what the README/SECURITY.md already claim.

## HIGH

### 1. Malformed RPC responses crash instead of surfacing as `AppError`

**Evidence:** `shared/src/commonMain/kotlin/dev/web3demo/chain/Abi.kt:31-36` (`decodeUint256`,
`decodeUint8`) and `:46-58` (`decodeString`) parse the RPC's `result` hex string with
`BigInteger.parseString(_, 16)` and raw substring indexing, with no validation that `result` is
actually well-formed hex. Every call site —
`TokenRepository.fetchMetadata`/`fetchBalance` (`chain/TokenRepository.kt:16-18,30`),
`NftRepository.fetchOwner`/`fetchTokenUri` (`chain/NftRepository.kt:24,32`) — calls these decode
functions directly on an `AppResult.Ok` value or inside an `AppResult.map { }` lambda, outside any
try/catch. `EthereumRpcClient.call` only wraps the *network* call in try/catch
(`chain/EthereumRpcClient.kt:58-67`); it never validates that `result` is parseable hex before
handing it back as `AppResult.Ok`.

**Impact:** `AppError`'s own doc comment states the point of the whole sealed-class design is "so
UI can render a typed reason ... instead of a raw exception message"
(`chain/AppError.kt:3-4`). This bypasses that entirely. Concretely: `EthereumRpcClient` targets a
public, unauthenticated, non-pinned RPC endpoint (`ethereum-sepolia-rpc.publicnode.com` —
`SECURITY.md` already documents no cert pinning); an endpoint hiccup that returns a non-standard
`result` field, or a proxy/MITM on an untrusted network returning garbage, throws a Kotlin
exception instead of an `AppError.InvalidData`. On iOS this is worse than a crashed coroutine:
none of `TokenRepository`'s or `NftRepository`'s suspend functions are annotated `@Throws`, and
Kotlin/Native's interop contract is that an *undeclared* exception crossing into Swift is a fatal,
uncatchable process termination — not a Swift `Error` the `try?` in `PortfolioView.swift:57,63`
can catch. So this is a real crash path on the Portfolio tab, triggered by ordinary network
flakiness, not a contrived input.

**Fix:** wrap the decode calls at the repository boundary (`TokenRepository`/`NftRepository`) in a
try/catch that maps parse failures to `AppResult.Err(AppError.InvalidData(...))`, the same pattern
`EthereumRpcClient.parseResponse` already uses for the JSON-shape checks. That keeps the fix at
the one layer whose whole job is "translate untrusted external data into `AppResult`," rather than
pushing defensive try/catch into every UI call site.

**Verification:** add a `commonTest` case feeding `EthereumRpcClient` (via a `MockEngine`, same
pattern as `NftRepositoryTest`) a response with a non-hex `result` field and assert
`TokenRepository.fetchBalance` returns `AppResult.Err`, not a thrown exception.

## MEDIUM

### 2. Android wallet-request params are built by raw string interpolation, not a JSON encoder

**Evidence:** `androidApp/src/main/kotlin/dev/web3demo/androidapp/ReownWalletGateway.kt:117-118`
(`signMessage`) and `:128-129` (`sendTransaction`):

```kotlin
val params = """["$message","${current.account}"]"""
...
val params = """[{"from":"${current.account}","to":"$to","value":"$valueWei","data":"$data"}]"""
```

None of `message`, `to`, `valueWei`, or `data` are JSON-escaped before being spliced into the
literal. A `message` containing a `"` or `\` produces malformed JSON or shifts what the wallet
receives as the params array (e.g. a message like `x","evil` turns a 2-element `personal_sign`
array into 3 elements). The iOS counterpart
(`App/Sources/ReownWalletGateway.swift:64-71`) does this correctly via the SDK's typed
`W3MJSONRPC.eth_sendTransaction(...)`/`.personal_sign(...)` builders — so the correct pattern was
available and used on one platform but not the other.

**Impact:** currently unreachable — nothing in `PortfolioScreen.kt` calls `signMessage` or
`sendTransaction` yet, only `disconnect()` and the read-only balance fetch. Graded MEDIUM rather
than HIGH because of that; this becomes a HIGH the moment either method gets wired to a UI action,
since it sits directly on the "what does the connected wallet actually get asked to sign" path.

**Fix:** build `params` with `kotlinx.serialization`'s `buildJsonArray`/`buildJsonObject` (already
a dependency, used throughout `EthereumRpcClient`) instead of string templates.

**Verification:** a unit test constructing `signMessage` params with a message containing `"` and
asserting the result round-trips through `Json.parseToJsonElement` without throwing would have
caught this immediately — worth adding once the fix lands.

### 3. `EthereumRpcClient`'s default `HttpClient` is recreated (and never closed) on every wallet session change

**Evidence:** `androidApp/src/main/kotlin/dev/web3demo/androidapp/PortfolioScreen.kt:73` —
`val repository = TokenRepository(EthereumRpcClient())` — sits inside
`LaunchedEffect(session) { ... }`, so it re-runs on every `WalletSession` transition (connect,
disconnect, reconnect). The iOS equivalent has the identical shape:
`App/Sources/PortfolioView.swift:56`, inside `.task(id: gateway.session) { ... }`. Both rely on
`EthereumRpcClient`'s default parameter `httpClient: HttpClient = HttpClient()`
(`chain/EthereumRpcClient.kt:26`) — a fresh Ktor client, with its own OkHttp/URLSession engine and
connection pool, on every recomposition of that effect. Ktor `HttpClient` instances aren't closed
automatically; nothing here ever calls `.close()`.

**Impact:** each connect/disconnect cycle on the Portfolio tab leaks one HTTP client's worth of
engine threads/connections. Low practical impact for a demo session (a handful of
connect/disconnect cycles), but it's the kind of thing that compounds in a longer-lived real app
and is a one-line fix.

**Fix:** hoist a single `EthereumRpcClient`/`TokenRepository` instance above the effect —
`remember { EthereumRpcClient() }` (Compose) / a `@State`-independent stored property or
`@StateObject`-scoped value (SwiftUI) — so it's constructed once per screen lifetime, not once per
session transition.

**Verification:** none needed beyond the fix itself; this isn't behavior-visible, just a resource
lifetime issue. Confirmed by inspection, not by a failing test — see `docs/refactoring-report.md`
for what changed.

### 4. `shared`'s Android target depends on `Dispatchers.Main` without declaring `kotlinx-coroutines-android`

**Evidence:** `PriceFeedController` (`shared/src/commonMain/kotlin/dev/web3demo/realtimefeed/PriceFeedController.kt:18`)
constructs `CoroutineScope(SupervisorJob() + Dispatchers.Main)`. `Dispatchers.Main` requires a
platform `MainDispatcherFactory` on the runtime classpath — on Android, that's
`kotlinx-coroutines-android`. Neither `shared/build.gradle.kts`'s `androidMain` source set nor
`androidApp/build.gradle.kts` declares that dependency directly.
`./gradlew :androidApp:dependencies --configuration debugRuntimeClasspath` confirms it resolves —
but only transitively, pulled in by several of `androidApp`'s own AndroidX/Reown dependencies.

**Impact:** works today, entirely by accident of what else `androidApp` happens to depend on. If
`shared` is ever consumed by a different Android target that doesn't happen to pull in Compose/
AndroidX/Reown (a plain JVM service, a minimal test harness, a different app module), any code
path touching `PriceFeedController` fails at runtime with `Dispatchers.Main`'s unhelpful "Module
with the Main dispatcher had failed to initialize."

**Fix:** add `implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")` to
`shared/build.gradle.kts`'s `androidMain` dependencies explicitly, so the requirement is honest
about where it actually comes from.

**Verification:** `./gradlew :androidApp:dependencies --configuration debugRuntimeClasspath | grep coroutines-android` before/after — after the fix, it should show up as a direct dependency of `shared`, not several layers deep under Reown/AndroidX.

## LOW

### 5. iOS `ReownWalletGateway.sendRequest` doesn't handle `Task` cancellation

**Evidence:** `App/Sources/ReownWalletGateway.swift:79-95`. The `withCheckedThrowingContinuation`
call sets up a Combine `sink` on `sessionResponsePublisher` with no cancellation handling. Compare
to the Android equivalent, which explicitly wires
`continuation.invokeOnCancellation { if (pendingRequest === continuation) pendingRequest = null }`
(`androidApp/.../ReownWalletGateway.kt:149-151`) — the same author solved this exact problem on
one platform and not the other.

**Impact:** if the `Task` awaiting `signMessage`/`sendTransaction` is cancelled (view disappears,
parent task cancelled) before the wallet responds, the Combine subscription and the suspended
continuation are never cleaned up — they sit alive until (if ever) `sessionResponsePublisher`
happens to emit. Not currently reachable from any UI (see finding #2 — nothing calls these yet on
either platform), so LOW rather than MEDIUM.

**Fix:** wrap in `withTaskCancellationHandler`, cancelling the Combine subscription in the
cancellation handler.

### 6. `DefaultCryptoProvider.recoverPubKey` crashes the process instead of throwing

**Evidence:** `App/Sources/DefaultCryptoProvider.swift:12-14` — `fatalError(...)` — even though
the protocol requirement is `func recoverPubKey(...) throws -> Data`.

**Impact:** currently unreachable (SIWE is off — `authRequestParams: nil`), documented as such in
the comment directly above it. But the throwing mechanism this method is declared with already
exists and is unused; a `fatalError` here means any future change that enables SIWE, or any
internal SDK call path that invokes this outside the SIWE flow specifically, crashes the app
instead of surfacing a normal, catchable Swift error — for no benefit over throwing, since the
infrastructure to throw is already sitting right there in the function signature.

**Fix:** `throw WalletGatewayError` (or a new dedicated error case) instead of `fatalError`.

### 7. `EthereumRpcClient.nextId` is a non-atomic mutable `var`

**Evidence:** `chain/EthereumRpcClient.kt:28,43` — `private var nextId = 1`, incremented with
`nextId++` on every `call()`.

**Impact:** currently harmless — `call()` sends one request and awaits its response synchronously
per invocation; nothing correlates responses back to a request `id`, so a lost increment from
concurrent access wouldn't produce a wrong result, just a duplicate id sent to the server (which
the server doesn't care about for single, non-batched requests). Flagged as LOW because it's a
latent data race that would matter the moment this client either batches requests or starts
correlating responses by id.

**Fix:** not urgent; if it's ever touched, `kotlin.concurrent.atomics.AtomicInt` (or just dropping
the field — nothing currently reads `id` back from the response) removes the question entirely.

### 8. `TokenRepository.fetchMetadata` makes three RPC round-trips sequentially

**Evidence:** `chain/TokenRepository.kt:5-7` — `name`, `symbol`, `decimals` are each `await`ed in
sequence, not concurrently.

**Impact:** pure latency — on the Portfolio tab, this triples the time-to-first-render for token
metadata versus fetching all three concurrently, for calls that don't depend on each other.

**Fix:** `coroutineScope { val name = async { rpc.call(...) }; val symbol = async { ... }; ... }`,
`awaitAll`.

### 9. Order book price levels are keyed by `Double`, inconsistent with the project's own stated precision principle

**Evidence:** `realtimefeed/OrderBookClient.kt:52-53` — `HashMap<Double, Double>` for both
`bids`/`asks`. `TokenAmount.kt`'s doc comment states the reasoning for using `BigInteger` instead
of `Double` for on-chain amounts explicitly; the order book uses the type that reasoning argues
against, for a different kind of value (market prices, not token amounts).

**Impact:** none currently — order book values are Binance API prices parsed straight from JSON
and only ever displayed, never summed/compared for equality across different string
representations, so `Double`'s precision is adequate here (unlike ERC-20 balances, where it
genuinely isn't). Flagged as a consistency note, not a bug: worth a one-line comment in
`OrderBookClient` explaining why `Double` is fine here specifically, so a future reader doesn't
assume it was an oversight given the BigInteger precedent set elsewhere in the same codebase.

## Strong decisions worth calling out

- **`OrderBookSync`/`OrderBookClient` implement Binance's actual documented reconciliation
  algorithm** (buffer → snapshot → find the straddling diff → verify each subsequent `U` picks up
  exactly at the previous `u+1`), not a simplified approximation, and the re-check-on-every-buffered-diff
  fix (`OrderBookClient.kt:120-124`) reads like a bug that was actually hit and fixed, not
  theorized about — the comment says as much.
- **`AuthenticatedConnectionGate`'s proactive/reactive refresh split** correctly distinguishes "the
  watcher fired" from "connect() returned" via an explicit boolean rather than overloading a
  shared signal, and the `invokeOnCompletion { watcherJob.cancel() }` line specifically closes a
  hang that a more naive version would have.
- **`IpfsGateway.normalize`'s scheme allow-list** is the right instinct for attacker-controlled
  NFT metadata (anyone can mint a token with a malicious `tokenURI`) — rejecting anything that
  isn't `http(s)`/`ipfs` before it ever reaches an image loader is exactly the boundary check that
  belongs there.
- **`TokenAmount`/`formatTokenAmount`'s string-only decimal formatting** avoids the classic
  `Double`-for-money mistake entirely — no float division anywhere near a real balance.
- **The `AppError`/`AppResult` sealed-class design is the right shape** for what it's trying to
  do — typed, exhaustive, UI-branchable failure reasons instead of stringly-typed exceptions. (See
  finding #1 for where its own call sites don't fully honor it yet.)
- **ktlint/SwiftLint are both wired into CI with zero suppressions left unexplained** — the two
  `// swiftlint:disable:next` uses in `WalletKeyStore.swift`/`Web3DemoApp.swift` both have an
  inline justification, not a blanket rule disable.

## Open questions

- Should `WalletGateway.signMessage`/`sendTransaction` (finding #2's location) be removed until
  something actually calls them, per the project's own "no code for hypothetical future
  requirements" principle (`CONTRIBUTING.md`) — or are they staying as the obvious next-roadmap-item
  scaffolding? If the latter, worth a `// TODO` or roadmap line saying so explicitly rather than
  leaving them looking like dead code.
- `TokenRepository`/`NftRepository` currently take an `EthereumRpcClient` by constructor
  injection but nothing hoists a shared instance above screen level on either platform (finding
  #3). Is there an appetite for a small app-level "services" container on each platform once a
  second screen needs the same client, or is per-screen construction fine for a demo of this size?
