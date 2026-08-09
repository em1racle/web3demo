package dev.web3demo.realtimefeed

data class AuthToken(val value: String, val expiresAtEpochMillis: Long)

interface AuthTokenProvider {
    /** Returns the freshest known token without necessarily hitting the network — implementers
     * typically cache internally and only call the refresh endpoint when needed. */
    suspend fun currentToken(): AuthToken?
    suspend fun refresh(): AuthToken
}

class AuthRejectedException(message: String) : Exception(message)
