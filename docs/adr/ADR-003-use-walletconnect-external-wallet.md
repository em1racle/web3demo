# ADR-003: WalletConnect (external signer) instead of an in-app custodial key for on-chain assets

## Status

Accepted

## Context

The dApp flow needs to read a real wallet's balance and preview a transaction. There are two
fundamentally different ways to get a "wallet" into the app: generate and hold a private key
in-app (custodial), or connect to a wallet the user already controls and never see the key
(non-custodial, via WalletConnect). This is a separate decision from the Wallet tab's Secure
Enclave/Keystore demo key, which is a device-bound signing key for a different purpose (JD
requirement: on-device credential handling) and was never intended to hold real on-chain assets.

## Decision

Use WalletConnect via Reown's official AppKit SDK to connect to an external wallet (MetaMask,
Rainbow, etc.). The app requests `signMessage`/`sendTransaction`, the external wallet prompts the
user and returns a signature or transaction hash — this app never sees, generates, or stores a
private key capable of moving on-chain funds.

## Alternatives considered

- **Generate a custodial key in-app** (extending the existing Secure Enclave/Keystore mechanism
  to also hold a chain key). Rejected: it would conflate two different security models under one
  UI, and a demo app is exactly the wrong place to normalize "the app holds your keys" as a
  pattern — real wallets exist so users don't have to trust individual apps with funds.
- **A read-only mode with a manually pasted address, no wallet connection at all.** Simpler, but
  skips the actual engineering risk this ADR is about: session lifecycle, pairing/deep-link flow,
  chain/account change handling, reconnect after app restart — all things a real dApp has to get
  right and none of which show up if you just paste an address into a text field.

## Consequences

- Requires a Reown Cloud project ID (free, but tied to an external account) — see
  `docs/architecture.md` for what's stubbed vs. wired to the real SDK if that account isn't set
  up when this is reviewed.
- Session state (topic, chain id, account, connection status) becomes real UI state that has to
  survive process death/recreation, not just a nice-to-have — see ADR-004.
- Wallet address alone is not proof of ownership (see the security note in
  `docs/architecture.md`) — anything beyond read-only display needs a SIWE signature check.

## Revisit triggers

- If a future requirement needs the app to sign automatically without user interaction each time
  (e.g., a session key / delegate signer pattern) — WalletConnect's per-action prompt model
  doesn't fit that without additional infrastructure.
