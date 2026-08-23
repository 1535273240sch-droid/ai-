package com.aisocial.agent.api

import com.aisocial.agent.data.AppPrefs
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** API 异常：非 2xx 或网络错误 */
class ApiException(val code: Int, message: String) : Exception(message)

/**
 * 后端 API 客户端（OkHttp + Gson，对齐 docs/API_CONTRACT.md）。
 * 所有方法走协程，内部切换 IO 线程。
 */
object ApiClient {

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // ---------------- DTO（对齐契约字段） ----------------
    data class User(val id: Long, val username: String, val role: String)

    data class AuthResponse(val token: String, val user: User)

    data class LoginRequest(val username: String, val password: String)

    data class ActivateRequest(val code: String, val device_fingerprint: String)

    data class LicenseOut(
        val code: String,
        @SerializedName("expires_at") val expiresAt: String,
        val features: Map<String, Any>?,
        val status: String,
    )

    data class ActivateResponse(val activated: Boolean, val license: LicenseOut?)

    data class LicenseInfoResponse(val valid: Boolean, val license: LicenseOut?)

    data class SuggestRequest(
        @SerializedName("contact_id") val contactId: Long,
        val message: String,
        val mode: String,
    )

    data class Decision(val mode: String, val reason: String)

    data class SuggestResponse(
        val suggestions: List<String>,
        val decision: Decision,
        @SerializedName("delay_ms") val delayMs: Long,
    )

    data class PlatformEventRequest(
        val platform: String,
        @SerializedName("platform_contact_id") val platformContactId: String,
        val type: String,
        val content: String?,
    )

    data class OkResponse(val ok: Boolean)

    // ---------------- 请求 ---------------- 
    /** 探测服务器可达性（GET /health，不带 /api/v1 前缀） */
    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(AppPrefs.serverUrl.trimEnd('/') + "/health")
            .get()
            .build()
        runCatching { client.newCall(req).execute().use { it.isSuccessful } }.getOrDefault(false)
    }

    suspend fun login(username: String, password: String): AuthResponse =
        post("/auth/login", LoginRequest(username, password))

    suspend fun activateLicense(code: String, deviceFingerprint: String): ActivateResponse =
        post("/license/activate", ActivateRequest(code, deviceFingerprint))

    suspend fun licenseInfo(): LicenseInfoResponse =
        get("/license/info")

    data class Contact(
        val id: Long,
        val platform: String,
        @SerializedName("platform_contact_id") val platformContactId: String,
        val nickname: String,
    )

    data class ContactListResponse(val items: List<Contact>)

    data class ContactProfile(
        val relationship: String? = null,
        @SerializedName("interaction_style") val interactionStyle: String? = null,
        @SerializedName("reply_frequency") val replyFrequency: String? = null,
        @SerializedName("sentence_style") val sentenceStyle: String? = null,
        val taboos: List<String> = emptyList(),
    )

    data class CreateContactRequest(
        val platform: String,
        @SerializedName("platform_contact_id") val platformContactId: String,
        val nickname: String,
        val profile: ContactProfile? = null,
    )

    suspend fun listContacts(): ContactListResponse =
        get("/contacts")

    suspend fun createContact(platform: String, platformContactId: String, nickname: String, profile: ContactProfile?): Contact =
        post("/contacts", CreateContactRequest(platform, platformContactId, nickname, profile))

    suspend fun suggest(contactId: Long, message: String, mode: String): SuggestResponse =
        post("/agent/suggest", SuggestRequest(contactId, message, mode))

    suspend fun reportEvent(platform: String, contactId: String, type: String, content: String?): OkResponse =
        post("/platform/events", PlatformEventRequest(platform, contactId, type, content))

    suspend fun reportReply(platform: String, contactId: String, content: String): OkResponse =
        post("/platform/reply", PlatformEventRequest(platform, contactId, "message_sent", content))

    // ---------------- 底层 ----------------
    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(AppPrefs.apiBase + path)
            .header("Authorization", "Bearer ${AppPrefs.jwtToken}")
            .get()
            .build()
        execute(req, T::class.java)
    }

    private suspend inline fun <reified T> post(path: String, body: Any): T = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(AppPrefs.apiBase + path)
            .header("Authorization", "Bearer ${AppPrefs.jwtToken}")
            .post(gson.toJson(body).toRequestBody(jsonType))
            .build()
        execute(req, T::class.java)
    }

    private fun <T> execute(req: Request, clazz: Class<T>): T {
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val detail = runCatching {
                    val obj = gson.fromJson(text, Map::class.java) as Map<*, *>
                    obj["detail"]?.toString()
                }.getOrNull()
                throw ApiException(resp.code, detail ?: "HTTP ${resp.code}")
            }
            return gson.fromJson(text, clazz)
        }
    }
}
