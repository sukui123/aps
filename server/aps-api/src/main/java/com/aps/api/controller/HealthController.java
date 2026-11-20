package com.aps.api.controller;

import com.aps.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 启动健康检查（公开接口，便于验证应用可启动）。 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @GetMapping("/health")
    public Result<String> health() {
        return Result.ok("ok");
    }
}
