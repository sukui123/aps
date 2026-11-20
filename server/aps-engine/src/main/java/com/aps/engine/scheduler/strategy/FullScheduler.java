package com.aps.engine.scheduler.strategy;

import com.aps.domain.engine.ScheduleResult;
import com.aps.domain.engine.Scheduler;
import com.aps.domain.engine.SolverContext;
import org.springframework.stereotype.Component;

/**
 * 全量排产（P0 默认路径，技术方案 §4.3/§4.4）。
 * 本轮只占位：求解流程（加载快照→可行性过滤→工序展开→分派→校验→计分→轨迹→落库）为后续迭代实现。
 */
@Component("fullScheduler")
public class FullScheduler implements Scheduler {

    @Override
    public ScheduleResult solve(SolverContext context) {
        ScheduleResult result = new ScheduleResult();
        result.setRunId(context.getRunId());
        result.setSuccess(true);
        result.setMessage("scaffold only: heuristic solver not implemented yet");
        return result;
    }
}
