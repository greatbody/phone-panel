package com.greatbody.phonepanel.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PanelButton(
    val id: String,
    val label: String = "",
    val icon: String = "",
    /** "emoji" 或 "image"。缺省按 emoji。 */
    val iconType: String? = null,
    val color: String = "#475569",
    val type: String = "shell",
    val command: String = "",
) {
    val isImageIcon: Boolean get() = iconType == "image"
}

@Serializable
data class PanelConfigResponse(
    val port: Int = 7788,
    val buttons: List<PanelButton> = emptyList(),
)

@Serializable
data class ExecuteResult(
    val ok: Boolean = false,
    val stdout: String? = null,
    val stderr: String? = null,
    val durationMs: Long = 0,
    val error: String? = null,
)

/** App 本地保存的配对信息 */
data class Pairing(
    val baseUrl: String,   // 形如 http://10.10.11.59:7788
    val token: String,
) {
    companion object {
        /** 解析二维码里的 URL：http://host:port/panel?token=xxx → Pairing */
        fun fromQrUrl(url: String): Pairing? {
            return try {
                val u = java.net.URI(url)
                val token = (u.rawQuery ?: "").split("&")
                    .firstNotNullOfOrNull { kv ->
                        val (k, v) = kv.split("=", limit = 2).let {
                            it[0] to (it.getOrNull(1) ?: "")
                        }
                        if (k == "token" && v.isNotEmpty()) v else null
                    } ?: return null
                val port = if (u.port == -1) (if (u.scheme == "https") 443 else 80) else u.port
                Pairing(
                    baseUrl = "${u.scheme}://${u.host}:$port",
                    token = token,
                )
            } catch (_: Throwable) {
                null
            }
        }
    }
}
