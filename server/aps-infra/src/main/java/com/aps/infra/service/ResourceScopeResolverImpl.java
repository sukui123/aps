package com.aps.infra.service;

import com.aps.domain.service.DataScope;
import com.aps.domain.service.ResourceScopeResolver;
import com.aps.infra.mapper.ResourceMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 应用层数据范围解析实现（PF-02 / #8）。
 *
 * <p>把 DataScope 的 factoryIds 经 {@link ResourceMapper#selectVisibleSubtreeIds} 展开为
 * 可见资源 id 集合。无工厂限制或租户上下文缺失时返回 {@code null}（表示不过滤，由 RLS
 * 兜底）；有工厂限制时返回子树集合（可能为空）。
 */
@Service
public class ResourceScopeResolverImpl implements ResourceScopeResolver {

    private final ResourceMapper mapper;

    public ResourceScopeResolverImpl(ResourceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Long> visibleResourceIds(DataScope scope) {
        if (scope.getTenantId() == null || !scope.hasFactoryRestriction()) {
            // 无租户上下文或工厂无限制：不做应用层过滤（RLS 负责租户级隔离）
            return null;
        }
        return mapper.selectVisibleSubtreeIds(scope.getTenantId(), scope.getFactoryIds());
    }

    @Override
    public boolean isVisible(DataScope scope, Long resourceId) {
        if (scope.getTenantId() == null || !scope.hasFactoryRestriction()) {
            return true;
        }
        if (resourceId == null) {
            return false;
        }
        List<Long> visible = mapper.selectVisibleSubtreeIds(scope.getTenantId(), scope.getFactoryIds());
        return visible != null && visible.contains(resourceId);
    }
}
