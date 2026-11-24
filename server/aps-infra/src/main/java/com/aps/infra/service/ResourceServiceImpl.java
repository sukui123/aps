package com.aps.infra.service;

import com.aps.common.error.CommonErrorCode;
import com.aps.common.exception.BusinessException;
import com.aps.common.result.PageResult;
import com.aps.domain.model.Resource;
import com.aps.domain.service.DataScope;
import com.aps.domain.service.ResourceScopeResolver;
import com.aps.domain.service.ResourceService;
import com.aps.infra.mapper.ResourceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 领域服务实现（示例切片）：仅做持久化编排，业务规则后续迭代填充。 */
@Service
public class ResourceServiceImpl implements ResourceService {

    private final ResourceMapper mapper;
    private final ResourceScopeResolver scopeResolver;

    public ResourceServiceImpl(ResourceMapper mapper, ResourceScopeResolver scopeResolver) {
        this.mapper = mapper;
        this.scopeResolver = scopeResolver;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<Resource> list(int page, int pageSize) {
        DataScope scope = DataScope.current();
        List<Long> visibleIds = scopeResolver.visibleResourceIds(scope);
        LambdaQueryWrapper<Resource> query = new LambdaQueryWrapper<>();
        // 应用层租户谓词：与 RLS 形成双保险（开发库属主连接绕过 RLS 时仍隔离）
        if (scope.getTenantId() != null) {
            query.eq(Resource::getTenantId, scope.getTenantId());
        }
        query.eq(Resource::getIsDeleted, false);
        if (visibleIds != null) {
            if (visibleIds.isEmpty()) {
                // 有工厂范围但无可见节点：直接返回空页，不查库
                return PageResult.of(List.of(), 0, page, pageSize);
            }
            query.in(Resource::getId, visibleIds);
        }
        Page<Resource> pageReq = new Page<>(page, pageSize);
        IPage<Resource> result = mapper.selectPage(pageReq, query);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public Resource get(Long id) {
        DataScope scope = DataScope.current();
        if (!scopeResolver.isVisible(scope, id)) {
            // 越权读取工厂范围外资源：403（错误码 10003）
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "资源不在数据权限范围内");
        }
        return mapper.selectById(id);
    }

    @Override
    public Long create(Resource resource) {
        mapper.insert(resource);
        return resource.getId();
    }

    @Override
    public void update(Resource resource) {
        mapper.updateById(resource);
    }
}
