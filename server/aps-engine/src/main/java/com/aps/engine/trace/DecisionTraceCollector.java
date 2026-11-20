package com.aps.engine.trace;

import org.springframework.stereotype.Component;

/** 决策轨迹埋点骨架（技术方案 §4.6）：P0 建表时预留，P1 完整展示。 */
@Component
public class DecisionTraceCollector {

    /** 后续迭代：收集候选资源集/排除原因码/命中规则/版本差异/未排卡点。 */
    public void record(Object stagePayload) {
        // no-op in scaffold
    }
}
