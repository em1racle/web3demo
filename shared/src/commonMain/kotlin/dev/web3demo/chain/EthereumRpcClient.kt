package dev.web3demo.chain

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray

/**
 * Minimal JSON-RPC client for `eth_call` against a public Ethereum-compatible node — no API key,
 * no account needed. Sepolia's publicnode.com endpoint is free and unauthenticated, which is why
 * this demo can read real on-chain state without wiring up an Infura/Alchemy account.
 */
class EthereumRpcClient(
    private val rpcUrl: String = "https://ethereum-sepolia-rpc.publicnode.com",
    private val httpClient: HttpClient = HttpClient(),
) {
    private var nextId = 1

    companion object {
        // / Kotlin/Native's Swift export drops default parameter values — the generated Swift
        // / initializer requires every constructor param explicitly, including `httpClient`,
        // / which Swift has no easy way to construct itself. This factory gives Swift a working
        // / zero-arg entry point without exposing that mismatch to call sites.
        fun default(): EthereumRpcClient = EthereumRpcClient()
    }

    suspend fun call(
        to: String,
        data: String,
        block: String = "latest",
    ): AppResult<String> {
        val requestId = nextId++
        val requestBody =
            buildJsonObject {
                put("jsonrpc", JsonPrimitive("2.0"))
                put("id", JsonPrimitive(requestId))
                put("method", JsonPrimitive("eth_call"))
                putJsonArray("params") {
                    addJsonObject {
                        put("to", JsonPrimitive(to))
                        put("data", JsonPrimitive(data))
                    }
                    add(JsonPrimitive(block))
                }
            }

        return try {
            val response =
                httpClient.post(rpcUrl) {
                    contentType(ContentType.Application.Json)
                    setBody(requestBody.toString())
                }
            parseResponse(response.bodyAsText())
        } catch (e: Exception) {
            AppResult.Err(AppError.Network(e))
        }
    }

    private fun parseResponse(text: String): AppResult<String> {
        val json =
            try {
                Json.parseToJsonElement(text).jsonObject
            } catch (e: Exception) {
                return AppResult.Err(AppError.InvalidData("non-JSON RPC response: ${e.message}"))
            }

        val error = json["error"]?.jsonObject
        if (error != null) {
            val code = error["code"]?.jsonPrimitive?.int ?: -1
            val message = error["message"]?.jsonPrimitive?.content ?: "unknown RPC error"
            return AppResult.Err(AppError.Rpc(code, message))
        }

        val result =
            json["result"]?.jsonPrimitive?.content
                ?: return AppResult.Err(AppError.InvalidData("RPC response missing 'result'"))
        return AppResult.Ok(result)
    }
}
