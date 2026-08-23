package com.aisocial.agent.data

/**
 * 联系人画像，字段对齐后端 ContactProfile。
 * 用于组装 AI 回复上下文（关系 / 互动风格 / 回复频率 / 句式 / 禁忌话题）。
 */
data class Profile(
    val relationship: String? = null,
    val interactionStyle: String? = null,
    val replyFrequency: String? = null,
    val sentenceStyle: String? = null,
    val taboos: List<String> = emptyList(),
) {
    fun toDisplayString(): String = buildString {
        relationship?.let { append("关系：$it\n") }
        interactionStyle?.let { append("互动风格：$it\n") }
        replyFrequency?.let { append("回复频率：$it\n") }
        sentenceStyle?.let { append("句式风格：$it\n") }
        if (taboos.isNotEmpty()) append("禁忌：${taboos.joinToString("、")}\n")
    }.trim()

    companion object {
        val DEFAULT = Profile(
            relationship = "普通朋友",
            interactionStyle = "随意自然，像正常聊天",
            replyFrequency = "正常",
            sentenceStyle = "简短口语化",
            taboos = listOf("不聊政治", "不谈收入", "不暴露 AI 身份"),
        )
    }
}
