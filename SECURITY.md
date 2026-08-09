# Security

This is a demo app, not a production wallet, but the parts of it that touch key material or
on-chain reads follow real security practice on purpose — that was the point of building it.
This doc says what's actually protected, what isn't, and why.

## What this app does and doesn't hold

- **No seed phrases or private keys capable of moving on-chain funds ever enter this app.**
  WalletConnect (Reown AppKit) delegates signing to an external wallet app; this app only ever
  sees session metadata, public addresses, and signatures/responses it didn't produce itself.
- **The Wallet tab's key is unrelated to the above and cannot move funds.** It's a hardware-backed
  P-256 key (Secure Enclave on iOS, Keystore+StrongBox on Android), biometric-gated, used to sign
  an arbitrary demo message — not a transaction, not derived into an address, not on a curve
  Ethereum/Bitcoin actually use (secp256k1). See the README's "Known gaps" section and the
  comments in `WalletKeyStore` for exactly what's missing to make it a real wallet key, and don't
  reuse this code path as one without addressing those gaps.
- **RPC reads are unauthenticated and read-only.** `EthereumRpcClient` calls a public Sepolia
  endpoint (`ethereum-sepolia-rpc.publicnode.com`) with `eth_call` only — no `eth_sendTransaction`,
  no keys involved on this app's side of that call at all.

## Threat model this app was built against

- **Compromised device (jailbreak/root):** the Wallet tab's key material is Secure
  Enclave/StrongBox-backed specifically so the raw key never lives in extractable app memory or
  storage, even with root — only the *use* of the key (a sign operation) is exposed, and that's
  gated by biometric prompt. This holds regardless of jailbreak status; it's a hardware guarantee,
  not an OS-level one.
- **Network attacker (MITM):** all traffic here (Binance WS, Sepolia RPC, WalletConnect relay) is
  TLS. No certificate pinning is implemented — a device with a user-trusted malicious root CA
  could still intercept. Acceptable for a demo reading public blockchain data; would need pinning
  for anything handling real funds.
- **Malicious/compromised WalletConnect relay or paired wallet:** out of scope for this app to
  defend against — that trust boundary belongs to the wallet the user pairs with, not to this app.
  This app's obligation is only to not hold key material itself, which it doesn't.

## What's explicitly out of scope

- Certificate pinning
- Jailbreak/root detection (deliberately not done — see the note above on why the Secure
  Enclave/StrongBox guarantee doesn't depend on it)
- Rate limiting / abuse protection on the RPC calls (they hit a public, third-party-rate-limited
  endpoint; this app doesn't run its own backend)
- Anything related to real fund custody — see the Wallet tab caveat above, repeated because it's
  the single easiest thing to misread about this project

## Reporting

This is a personal demo project with no users and no production deployment — there's no
disclosure program. If you're reading this as part of evaluating the code, the honest findings
list is in `docs/review.md`, not here.
