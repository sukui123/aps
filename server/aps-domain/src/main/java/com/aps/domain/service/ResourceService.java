package com.aps.domain.service;

import com.aps.common.result.PageResult;
import com.aps.domain.model.Resource;

/** 资源领域服务端口（实现在 aps-infra，Web 层只依赖此接口）。 */
public interface ResourceService {

    PageResult<Resource> list(int page, int pageSize);

    Resource get(Long id);

    Long create(Resource resource);

    void update(Resource resource);
}
