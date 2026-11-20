package com.aps.infra.mapper;

import com.aps.domain.model.Resource;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 资源 Mapper（MyBatis-Plus BaseMapper 提供基础 CRUD，复杂 SQL 后续在此扩展）。 */
@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {
}
