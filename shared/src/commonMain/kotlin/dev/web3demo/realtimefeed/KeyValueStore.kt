package dev.web3demo.realtimefeed

/**
 * Deliberately a plain `interface`, not an `expect class`: `expect`/`actual` classes need
 * matching constructors on every platform, which doesn't work here — Android's implementation
 * needs a `Context`, iOS's doesn't need anything platform-specific at all. An interface lets each
 * platform construct its implementation however makes sense locally (DI, `Context`, whatever)
 * and just hand the shared code an instance. Kotlin/Native also exports this interface as a
 * genuine Swift protocol in the XCFramework, so the iOS implementation is written in Swift, not
 * forced through a Kotlin shim.
 */
interface KeyValueStore {
    fun getString(key: String): String?

    fun putString(
        key: String,
        value: String,
    )
}
