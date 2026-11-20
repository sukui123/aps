package com.aps.api.controller;

import com.aps.common.error.CommonErrorCode;
import com.aps.common.exception.BusinessException;
import com.aps.common.result.PageResult;
import com.aps.common.result.Result;
import com.aps.domain.model.Resource;
import com.aps.domain.service.ResourceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 资源主数据示例 Controller（对齐技术方案 §5.2 #4 /api/master/resources）。 */
@RestController
@RequestMapping("/api/v1/master/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @GetMapping
    public Result<PageResult<Resource>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(resourceService.list(page, Math.min(pageSize, 200)));
    }

    @GetMapping("/{id}")
    public Result<Resource> get(@PathVariable Long id) {
        Resource resource = resourceService.get(id);
        if (resource == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        }
        return Result.ok(resource);
    }

    @PostMapping
    public Result<Long> create(@RequestBody Resource resource) {
        return Result.ok(resourceService.create(resource));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Resource resource) {
        resource.setId(id);
        resourceService.update(resource);
        return Result.ok();
    }
}
