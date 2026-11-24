package com.aps.api;

import com.aps.domain.service.DataScope;
import com.aps.domain.service.ResourceScopeResolver;
import com.aps.infra.mapper.ResourceMapper;
import com.aps.infra.service.ResourceScopeResolverImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 应用层 ABAC 数据范围解析单测（PF-02 / 验收 #8，B1 门禁边界内）。
 * 只验证解析逻辑：无限制放行、有范围展开为子树、越权 id 拒绝。
 */
class ResourceScopeResolverTest {

    private final ResourceMapper mapper = mock(ResourceMapper.class);
    private final ResourceScopeResolver resolver = new ResourceScopeResolverImpl(mapper);

    @Test
    void noTenantContextMeansNoApplicationFilter() {
        DataScope scope = new DataScope(null, List.of(10L));
        assertNull(resolver.visibleResourceIds(scope));
        assertTrue(resolver.isVisible(scope, 999L));
        verifyNoInteractions(mapper);
    }

    @Test
    void noFactoryRestrictionMeansNoApplicationFilter() {
        DataScope scope = new DataScope(1L, List.of());
        assertNull(resolver.visibleResourceIds(scope));
        assertTrue(resolver.isVisible(scope, 999L));
        verifyNoInteractions(mapper);
    }

    @Test
    void factoryRestrictionExpandsToVisibleSubtree() {
        DataScope scope = new DataScope(1L, List.of(10L, 20L));
        when(mapper.selectVisibleSubtreeIds(1L, List.of(10L, 20L)))
                .thenReturn(List.of(10L, 11L, 12L, 20L, 21L));

        assertEquals(List.of(10L, 11L, 12L, 20L, 21L), resolver.visibleResourceIds(scope));
        verify(mapper).selectVisibleSubtreeIds(1L, List.of(10L, 20L));
    }

    @Test
    void isVisibleChecksMembershipInExpandedSubtree() {
        DataScope scope = new DataScope(1L, List.of(10L));
        when(mapper.selectVisibleSubtreeIds(eq(1L), anyList()))
                .thenReturn(List.of(10L, 11L));

        assertTrue(resolver.isVisible(scope, 10L));
        assertTrue(resolver.isVisible(scope, 11L));
        assertFalse(resolver.isVisible(scope, 99L));
        assertFalse(resolver.isVisible(scope, null));
    }

    @Test
    void emptyExpansionDeniesEveryId() {
        DataScope scope = new DataScope(1L, List.of(404L));
        when(mapper.selectVisibleSubtreeIds(any(), anyList())).thenReturn(List.of());
        assertTrue(resolver.visibleResourceIds(scope).isEmpty());
        assertFalse(resolver.isVisible(scope, 404L));
    }
}
