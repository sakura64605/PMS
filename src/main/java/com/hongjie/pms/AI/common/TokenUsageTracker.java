package com.hongjie.pms.AI.common;

import dev.langchain4j.model.output.TokenUsage;
import org.springframework.stereotype.Component;

/**
 * 请求级 token 用量累积器。
 *
 * <p>ReAct Agent 处理一个用户消息会触发多次 LLM 调用（每次 Thought→Action 一次 + 最终出答案一次），
 * 单看最后一次调用的 token 会漏算中间步骤。本组件用 ThreadLocal 持有「当前请求」的累积器，
 * 由 {@code ChatModelListener} 在每次 LLM 响应时累加，process() 结束后取合计写入消息记录。</p>
 */
@Component
public class TokenUsageTracker {

    private final ThreadLocal<TokenUsageAccumulator> current = new ThreadLocal<>();

    /** 开启一次请求级累计，返回本次累积器 */
    public TokenUsageAccumulator begin() {
        TokenUsageAccumulator accumulator = new TokenUsageAccumulator();
        current.set(accumulator);
        return accumulator;
    }

    /** 请求结束清理，避免 ThreadLocal 泄漏到线程池复用 */
    public void end() {
        current.remove();
    }

    /** 当前请求的累积器；非 AI 请求线程为 null（此时 listener 不累加） */
    public TokenUsageAccumulator current() {
        return current.get();
    }

    public static class TokenUsageAccumulator {
        private int inputTokens;
        private int outputTokens;
        private int totalTokens;

        public int inputTokens() {
            return inputTokens;
        }

        public int outputTokens() {
            return outputTokens;
        }

        public int totalTokens() {
            return totalTokens;
        }

        public void add(TokenUsage usage) {
            if (usage == null) {
                return;
            }
            if (usage.inputTokenCount() != null) {
                inputTokens += usage.inputTokenCount();
            }
            if (usage.outputTokenCount() != null) {
                outputTokens += usage.outputTokenCount();
            }
            if (usage.totalTokenCount() != null) {
                totalTokens += usage.totalTokenCount();
            }
        }
    }
}
