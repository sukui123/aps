package com.aps.infra.integration;

import com.aps.domain.model.IntegrationConfig;
import com.aps.domain.port.IntegrationAdapter;
import com.aps.domain.storage.ObjectStorage;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** 文件兜底适配器（PF-04/US-PF04-5）：本地 FS 读写 JSON 夹具，不依赖任何外部系统。 */
@Component
public class FileAdapter implements IntegrationAdapter {

    private final ObjectStorage storage;

    public FileAdapter(ObjectStorage storage) {
        this.storage = storage;
    }

    @Override
    public String type() {
        return "FILE";
    }

    @Override
    public String fetch(IntegrationConfig config) throws Exception {
        String key = requireEndpoint(config);
        if (!storage.exists(key)) {
            throw new IllegalStateException("integration source file not found: " + key);
        }
        try (InputStream in = storage.get(key)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Override
    public boolean push(IntegrationConfig config, String payloadJson) throws Exception {
        String key = requireEndpoint(config) + ".writeback-" + System.currentTimeMillis() + ".json";
        storage.put(key, new java.io.ByteArrayInputStream(payloadJson.getBytes(StandardCharsets.UTF_8)));
        return true;
    }

    private String requireEndpoint(IntegrationConfig config) {
        String endpoint = config.getEndpoint();
        if (endpoint == null || endpoint.isBlank() || endpoint.contains("..")) {
            throw new IllegalArgumentException("invalid FILE endpoint");
        }
        return endpoint;
    }
}
