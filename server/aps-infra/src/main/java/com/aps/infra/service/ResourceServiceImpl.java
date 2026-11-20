package com.aps.infra.service;

import com.aps.common.result.PageResult;
import com.aps.domain.model.Resource;
import com.aps.domain.service.ResourceService;
import com.aps.infra.mapper.ResourceMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;

/** 领域服务实现（示例切片）：仅做持久化编排，业务规则后续迭代填充。 */
@Service
public class ResourceServiceImpl implements ResourceService {

    private final ResourceMapper mapper;

    public ResourceServiceImpl(ResourceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public PageResult<Resource> list(int page, int pageSize) {
        Page<Resource> pageReq = new Page<>(page, pageSize);
        IPage<Resource> result = mapper.selectPage(pageReq, null);
        return PageResult.of(result.getRecords(), result.getTotal(), page, pageSize);
    }

    @Override
    public Resource get(Long id) {
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
