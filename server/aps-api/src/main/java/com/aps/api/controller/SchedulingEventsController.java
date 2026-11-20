package com.aps.api.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 排产进度 SSE 订阅骨架（技术方案 §2.4.1）。
 * 本轮只建立通道并发送 connected 事件；后续迭代从 scheduling_run 读进度按阶段推送。
 */
@RestController
@RequestMapping("/api/v1/scheduling/runs")
public class SchedulingEventsController {

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(300_000L);
        try {
            emitter.send(SseEmitter.event().name("connected").data("runId=" + id));
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
