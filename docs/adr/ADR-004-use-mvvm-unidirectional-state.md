# ADR-004: MVVM with unidirectional state, not a heavier pattern

## Status

Accepted

## Context

Both UI frameworks in play (SwiftUI, Jetpack Compose) are declarative and re-render from state —
they need a predictable, observable state container per screen, not an object that mutates
freely from anywhere. At the same time, this project is small enough that a full Redux-style
store with actions/reducers/middleware would be pure ceremony.

## Decision

Each screen owns one state holder (`@Observable` class on iOS, a `StateFlow`-backed holder on
Android) that exposes an immutable state snapshot and a small number of intent
functions (`connect()`, `refresh()`, `sign(message:)`). The state holder is the only thing
allowed to mutate its own state; the view only reads and calls intents. This is standard MVVM,
kept unidirectional by convention (state flows down, intents flow up) rather than enforced by a
framework.

## Alternatives considered

- **Two-way bound mutable properties directly on the view.** Fastest to write, but re-render
  bugs (a value changing without the view being notified, or the view mutating state the state
  holder doesn't know about) are exactly the class of bug this project is trying to demonstrate
  *not* having, given the JD's emphasis on re-render correctness.
- **A full unidirectional-data-flow framework (Redux/TCA-style) on both platforms.** Would give
  stronger guarantees around action replay/debugging, but the ceremony (actions, reducers,
  middleware, two separate framework choices for Swift vs. Kotlin) isn't justified by an app this
  size, and would spend review time on framework mechanics instead of the actual engineering
  questions this demo is answering.

## Consequences

- ViewModels/state holders must not become service locators — a state holder should own exactly
  one screen's state, not become a shared grab-bag other screens reach into. Worth watching as
  the wallet/token/NFT state holders are added; see `docs/review.md` for whether this held.
- Pending/critical state (an in-flight transaction hash, a wallet session) has to be explicit
  fields in the state snapshot, not something reconstructed from scattered flags, so it survives
  rotation/process recreation on Android and scene reconnection on iOS without special-casing.

## Revisit triggers

- If cross-screen state sharing becomes common enough that each screen re-deriving it from its
  own state holder causes real duplication or drift.
