package com.pdfconverter.service.llm;

/**
 * DeepSeek 调试日志：入参/出参仅在 DEBUG 级别输出，避免生产环境刷屏。
 */
final class LlmCallLogUtil {

    private LlmCallLogUtil() {
    }

    static void logRequestDebug(org.slf4j.Logger log, LlmCallContext ctx, String model, int maxTokens,
                                String url, String systemPrompt, String userPrompt) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug("DeepSeek 请求入参 {} model={} max_tokens={} url={}\n【system】\n{}\n【user】\n{}",
                ctx.summary(), model, maxTokens, url, systemPrompt, userPrompt);
    }

    static void logResponseDebug(org.slf4j.Logger log, LlmCallContext ctx, String content, boolean retry) {
        if (!log.isDebugEnabled()) {
            return;
        }
        String tag = retry ? "重试响应" : "响应";
        if (content == null) {
            log.debug("DeepSeek {} {} content=null", tag, ctx.summary());
        } else {
            log.debug("DeepSeek {} {} 长度={} 内容=\n{}", tag, ctx.summary(), content.length(), content);
        }
    }

    static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
