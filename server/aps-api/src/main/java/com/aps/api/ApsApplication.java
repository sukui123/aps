package com.aps.api;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** APS 单进程主启动类：一个 Spring Boot 应用，引擎 worker 随进程启动（技术方案 §2.2）。 */
@SpringBootApplication
@ComponentScan("com.aps")
@MapperScan("com.aps.infra.mapper")
@EnableScheduling
public class ApsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApsApplication.class, args);
    }
}
