package dev.web3demo.chain

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

class TokenRepository(private val rpc: EthereumRpcClient) {
    suspend fun fetchMetadata(contract: String): AppResult<TokenMetadata> =
        coroutineScope {
            // name/symbol/decimals don't depend on each other — fetched concurrently rather than
            // three sequential round-trips.
            val nameDeferred = async { rpc.call(contract, Abi.encodeCall(Abi.SELECTOR_NAME)) }
            val symbolDeferred = async { rpc.call(contract, Abi.encodeCall(Abi.SELECTOR_SYMBOL)) }
            val decimalsDeferred = async { rpc.call(contract, Abi.encodeCall(Abi.SELECTOR_DECIMALS)) }

            val name = nameDeferred.await()
            val symbol = symbolDeferred.await()
            val decimals = decimalsDeferred.await()

            if (name is AppResult.Err) return@coroutineScope name
            if (symbol is AppResult.Err) return@coroutineScope symbol
            if (decimals is AppResult.Err) return@coroutineScope decimals

            try {
                AppResult.Ok(
                    TokenMetadata(
                        contract = contract,
                        name = Abi.decodeString((name as AppResult.Ok).value),
                        symbol = Abi.decodeString((symbol as AppResult.Ok).value),
                        decimals = Abi.decodeUint8((decimals as AppResult.Ok).value),
                    ),
                )
            } catch (e: Exception) {
                AppResult.Err(AppError.InvalidData("malformed RPC response: ${e.message}"))
            }
        }

    suspend fun fetchBalance(
        contract: String,
        owner: String,
        decimals: Int,
    ): AppResult<TokenBalance> {
        val call = Abi.encodeCall(Abi.SELECTOR_BALANCE_OF, Abi.encodeAddress(owner))
        return rpc.call(contract, call).decoded { hex ->
            TokenBalance(raw = Abi.decodeUint256(hex), decimals = decimals)
        }
    }
}
