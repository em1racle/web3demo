package dev.web3demo.chain

/** Failure modes callers actually need to branch on, so UI can render a typed reason (retry vs.
 * "connect your wallet" vs. generic error) instead of a raw exception message. */
sealed class AppError {
    data class Network(val cause: Throwable) : AppError()

    data class Rpc(val code: Int, val rpcMessage: String) : AppError()

    data class InvalidData(val reason: String) : AppError()

    data class WalletRejected(val reason: String) : AppError()

    data object Unauthenticated : AppError()
}

sealed class AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>()

    data class Err(val error: AppError) : AppResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): AppResult<R> =
        when (this) {
            is Ok -> Ok(transform(value))
            is Err -> this
        }

    inline fun onError(action: (AppError) -> Unit): AppResult<T> {
        if (this is Err) action(error)
        return this
    }
}

/** Like [AppResult.map], but for transforms that can throw on unexpected input (ABI decoding of
 * an RPC-supplied hex string, for example) — converts that throw into [AppError.InvalidData]
 * instead of letting it escape uncaught. Kept separate from [AppResult.map] rather than making
 * every `map` call catch-and-wrap: most transforms in this codebase are pure and can't fail, and
 * silently swallowing a real bug's exception there would be worse than letting it crash. */
inline fun <T> AppResult<String>.decoded(transform: (String) -> T): AppResult<T> =
    when (this) {
        is AppResult.Ok ->
            try {
                AppResult.Ok(transform(value))
            } catch (e: Exception) {
                AppResult.Err(AppError.InvalidData("malformed RPC response: ${e.message}"))
            }
        is AppResult.Err -> this
    }
