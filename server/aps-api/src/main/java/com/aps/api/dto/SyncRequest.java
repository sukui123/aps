package com.aps.api.dto;

import java.util.Map;

/** 触发同步请求（PF-04）：bizKey + 业务载荷，幂等键可走请求头。 */
public record SyncRequest(String bizType, String bizKey, String idempotencyKey,
                          Map<String, Object> payload) {
}
