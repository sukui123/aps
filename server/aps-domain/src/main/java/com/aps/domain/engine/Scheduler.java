package com.aps.domain.engine;

/** 排产模式统一接口（技术方案 §4.3）；P0 仅 FullScheduler，P1 按需加策略不改主流程。 */
public interface Scheduler {

    ScheduleResult solve(SolverContext context);
}
