# ADR-002: Repository boundary in front of RPC / SDK types

## Status

Accepted

## Context

Reading chain state (`TokenRepository`, `NftRepository`) and connecting an external wallet
(`WalletGateway`) both depend on SDK/transport details — a JSON-RPC endpoint URL, ABI hex
encoding, Reown AppKit's session types — that have nothing to do with what the UI actually needs
(a balance, a list of NFTs, a connected account). If those details leak into call sites, every
screen that shows a balance also has to know how `eth_call` works, and swapping the RPC provider
or the wallet SDK means touching UI code.

## Decision

Every external dependency sits behind a narrow interface defined in `shared/commonMain`, with the
concrete implementation injected:

- `WalletGateway` — `connect`, `reconnect`, `disconnect`, `currentSession`, `accountChanged`,
  `chainChanged`, `signMessage`, `sendTransaction`. Reown's `Modal`/`Sign` SDK types never appear
  in this interface's signature.
- `TokenRepository` / `NftRepository` — return domain types (`TokenBalance`, `NftItem`), never
  raw hex or a `JsonElement`.

Fakes for each interface exist for previews and tests, so UI code and its tests never depend on
network access or a real wallet being installed.

## Alternatives considered

- **Call the SDKs directly from ViewModels.** Fastest to write, but couples every screen to
  SDK-specific types and makes the "read balance" path untestable without a live RPC endpoint.
- **A full Clean-Architecture layering (domain/data/presentation packages, use-case classes per
  action).** Heavier than this project's actual complexity justifies right now — a handful of
  repositories behind interfaces gets the same testability and swappability without the extra
  indirection of use-case objects that each wrap a single repository call.

## Consequences

- One extra interface + fake per external dependency, which is overhead for a demo this size but
  pays for itself the moment a test needs to run without a live wallet or RPC endpoint (most of
  them do).
- If Reown's AppKit ever needs replacing (a different WalletConnect SDK, or a custom
  implementation), only the `WalletGateway` implementation changes — no call site does.

## Revisit triggers

- If the number of repositories grows past what a flat `shared/` package can hold clearly —
  see the open question in `docs/architecture.md` about splitting into `shared:chain`.
