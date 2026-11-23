package com.aps.infra.integration;

import com.aps.domain.model.IntegrationConfig;
import com.aps.domain.port.IntegrationAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * REST 适配器占位（PF-04）。品牌/接口文档未就绪，不伪装成生产实现；
 * 本轮可运行兜底为 FILE 与 DB_VIEW 两种适配器。
 */
@Component
public class RestStubAdapter implements IntegrationAdapter {

    private static final Logger log = LoggerFactory.getLogger(RestStubAdapter.class);

    @Override
    public String type() {
        return "REST";
    }

    @Override
    public String fetch(IntegrationConfig config) {
        log.warn("REST adapter is a stub (config={}); use FILE or DB_VIEW until the vendor API is available",
                config.getCode());
        return "{\"stub\":true,\"adapter\":\"REST\",\"config\":\"" + config.getCode() + "\"}";
    }

    @Override
    public boolean push(IntegrationConfig config, String payloadJson) {
        throw new IllegalStateException("REST adapter is a stub; vendor API contract is not available yet. "
                + "Use FILE or DB_VIEW for this integration.");
    }
}
