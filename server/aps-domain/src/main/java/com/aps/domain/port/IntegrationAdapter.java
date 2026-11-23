package com.aps.domain.port;

import com.aps.domain.model.IntegrationConfig;

/**
 * 集成数据源适配器端口：REST / DB_VIEW / FILE 三种实现由 infra 提供。
 * REST 为待品牌文档就绪的桩；DB 视图与文件是本轮可运行的兜底路径。
 */
public interface IntegrationAdapter {

    /** REST / DB_VIEW / FILE */
    String type();

    /** 拉取外部数据，返回 JSON 文本。 */
    String fetch(IntegrationConfig config) throws Exception;

    /** 回写结果，成功返回 true。 */
    boolean push(IntegrationConfig config, String payloadJson) throws Exception;
}
