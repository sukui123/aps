package com.aps.engine.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 应用内排产 worker（技术方案 §2.2/§1.7）：DB 任务表轮询领取 + PG advisory lock/行锁防重。
 * 本轮只占位；后续在检查点读取 scheduling_run.cancel_flag 实现协作式取消。
 */
@Component
public class ScheduleWorker {

    private static final Logger log = LoggerFactory.getLogger(ScheduleWorker.class);

    @Scheduled(fixedDelayString = "${aps.worker.poll-ms:5000}")
    public void poll() {
        log.debug("scheduling worker poll (scaffold) — claim via DB task table + advisory lock pending");
    }
}
