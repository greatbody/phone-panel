package com.greatbody.phonepanel.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class PanelClient(private val pairing: Pairing) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    /** 拼图标完整 URL，Coil 直接消费 */
    fun iconUrl(buttonId: String): String = "${pairing.baseUrl}/icons/$buttonId.png"

    private fun request(method: String, path: String, body: String? = null): Request.Builder {
        val b = Request.Builder()
            .url(pairing.baseUrl + path)
            .header("X-Panel-Token", pairing.token)
        when (method.uppercase()) {
            "GET" -> b.get()
            "POST" -> b.post((body ?: "").toRequestBody())
            else -> error("unsupported method $method")
        }
        return b
    }

    suspend fun fetchConfig(): Result<PanelConfigResponse> = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(request("GET", "/api/config").build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                json.decodeFromString(PanelConfigResponse.serializer(), resp.body!!.string())
            }
        }
    }

    suspend fun execute(buttonId: String): Result<ExecuteResult> = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(request("POST", "/api/execute/$buttonId").build()).execute().use { resp ->
                if (!resp.isSuccessful) error("HTTP ${resp.code}")
                json.decodeFromString(ExecuteResult.serializer(), resp.body!!.string())
            }
        }
    }
}
