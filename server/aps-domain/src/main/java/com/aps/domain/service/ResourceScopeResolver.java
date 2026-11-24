package com.aps.domain.service;

import java.util.List;

/**
 * 应用层数据范围解析（PF-02 / 验收 #8，ABAC 双保险）。
 *
 * <p>把当前 {@link DataScope} 的 factoryIds（工厂/车间级的 resource 节点 id）解析为
 * “可见资源 id 集合”：工厂视为 tenant 内的 resource 树层级，可见范围 = 这些节点
 * 及其所有后代节点。返回 {@code null} 表示无工厂限制（可见全部，由 RLS 兜底）；
 * 返回空集合表示有范围但无可见节点。
 *
 * <p>B1 门禁：本解析器只在应用层过滤，不写 RLS 策略、不新增映射表。factoryIds 的
 * 权威来源待 E1 主数据落地后替换——当前来自 JWT claims，其过渡来源是
 * {@link PermissionService#factoryIdsOf(Long, Long)}（读 aps.data-scope.&lt;role&gt;.factory-ids）。
 */
public interface ResourceScopeResolver {

    /**
     * 解析可见资源 id 集合。
     *
     * @param scope 当前数据范围（tenantId + factoryIds）
     * @return 可见 resource id 集合；null=无工厂限制（不过滤）；空集合=无可见节点
     */
    List<Long> visibleResourceIds(DataScope scope);

    /**
     * 判断单个资源 id 是否在可见范围内（越权读取拒绝用）。
     *
     * @param scope 当前数据范围
     * @param resourceId 目标资源 id
     * @return 无 factory 限制时恒 true；否则当且仅当 id 属于可见子树时 true
     */
    boolean isVisible(DataScope scope, Long resourceId);
}
