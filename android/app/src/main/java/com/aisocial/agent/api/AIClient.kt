package com.aisocial.agent.api

import com.aisocial.agent.data.AppPrefs
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** API 调用异常：非 2xx 或解析失败 */
class ApiException(val code: Int, message: String) : Exception(message)

/**
 * 本地 AI 客户端：手机直连 OpenAI 兼容接口（/v1/chat/completions）。
 * API 地址 / Key / 模型名全部在 ConfigActivity 本地配置，不经任何中间服务器。
 */
object AIClient {

    private val gson = Gson()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    const val DEFAULT_MODEL = "gpt-4o-mini"
    const val DEFAULT_PERSONA =
        "你是一个真实、自然的普通人。回复口语化、简短（一般不超过30字），贴合聊天语境，不暴露AI身份。"

    // ---------------- 核心调用 ----------------

    /** 调 chat/completions，返回第一条回复文本 */
    suspend fun chatCompletion(systemPrompt: String, userPrompt: String): String =
        withContext(Dispatchers.IO) {
            val body = mapOf(
                "model" to AppPrefs.modelName.ifBlank { DEFAULT_MODEL },
                "temperature" to 0.8,
                "messages" to listOf(
                    mapOf("role" to "system", "content" to systemPrompt),
                    mapOf("role" to "user", "content" to userPrompt),
                ),
            )
            val url = AppPrefs.apiUrl.trim().trimEnd('/') + "/chat/completions"
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer ${AppPrefs.apiKey.trim()}")
                .header("Content-Type", "application/json")
                .post(gson.toJson(body).toRequestBody(jsonType))
                .build()

            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    throw ApiException(resp.code, text.take(300))
                }
                val root = runCatching { gson.fromJson(text, JsonObject::class.java) }.getOrNull()
                    ?: throw ApiException(resp.code, "响应不是合法 JSON")
                root.getAsJsonArray("choices")?.firstOrNull()
                    ?.asJsonObject?.get("message")?.asJsonObject?.get("content")?.asString
                    ?: throw ApiException(resp.code, "响应缺少 choices[0].message.content")
            }
        }

    /**
     * 生成 3 条候选回复（对齐 WeChatAIAutoReply 模式）。
     * 容错解析：优先 JSON 数组 → 退回按行拆分 → 退回整段文本。
     */
    suspend fun suggestReplies(incomingMessage: String): List<String> {
        val persona = AppPrefs.personaPrompt.ifBlank { DEFAULT_PERSONA }
        val system = buildString {
            append("你是社交聊天回复助手，帮我（不是你）回复对方的消息。\n")
            append("我的人设：$persona\n")
            append("要求：生成 3 条候选回复，口语化、自然、贴合语境，每条不超过 50 字，")
            append("不暴露 AI 身份。只输出 JSON 字符串数组，格式：[\"回复1\",\"回复2\",\"回复3\"]")
        }
        val content = chatCompletion(system, "对方发来：$incomingMessage")
        return parseSuggestions(content).ifEmpty { listOf(content.trim()) }
    }

    /** 测试 API 连通性：让模型回一句话 */
    suspend fun testApi(): String =
        chatCompletion("你是连通性测试助手。", "请只回复四个字：连接成功")

    // ---------------- 解析 ----------------

    fun parseSuggestions(content: String): List<String> {
        // 1) 提取 JSON 数组（兼容 ```json 代码块包裹）
        val jsonCandidate = extractJsonArray(content)
        if (jsonCandidate != null) {
            val list = runCatching {
                val arr = gson.fromJson<List<String>>(jsonCandidate, List::class.java)
                arr.map { it.trim() }.filter { it.isNotBlank() }
            }.getOrNull()
            if (!list.isNullOrEmpty()) return list.take(3)
        }
        // 2) 退回：按行拆分，去掉序号前缀
        val lines = content.lines()
            .map { it.trim().trimStart('1', '2', '3', '4', '5', '.', '、', '）', ')', ' ', '\t') }
            .filter { it.isNotBlank() && it.length >= 2 }
        return if (lines.size >= 1) lines.take(3) else emptyList()
    }

    /** 从文本中抠出 [ ... ] JSON 数组片段 */
    private fun extractJsonArray(content: String): String? {
        val cleaned = content
            .replace("```json", "").replace("```", "")
            .trim()
        val start = cleaned.indexOf('[')
        val end = cleaned.lastIndexOf(']')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else null
    }
}
