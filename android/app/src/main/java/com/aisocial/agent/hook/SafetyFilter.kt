package com.aisocial.agent.hook

/**
 * 发送安全闸门（审查 H1 修复）：
 * AI 输出在自动发送前做本地轻量过滤——命中链接/资金/自曝身份类内容时
 * 拒绝自动发送，降级为人工确认（悬浮窗）或仅记录。
 */
object SafetyFilter {

    /** 绝不自动发送：链接/资金往来/中奖兼职诈骗话术/AI 自曝 */
    private val hardBlock = listOf(
        Regex("""https?://|www\.\S+|\S+\.(com|net|cn|xyz|top)\b""", RegexOption.IGNORE_CASE),
        Regex("""转账|汇款|收款码|支付宝|微信支付|银行卡号|验证码|充值|退款"""),
        Regex("""中奖|抽奖|兼职|刷单|返现|佣金|投资|理财|稳赚"""),
        Regex("""我是AI|我是机器人|作为AI|作为一个AI|AI助手|人工智能""", RegexOption.IGNORE_CASE),
    )

    /** @return true=安全可自动发送 */
    fun isSafeToSend(text: String): Boolean =
        text.isNotBlank() && hardBlock.none { it.containsMatchIn(text) }
}
