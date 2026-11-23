package com.aps.infra.mapper;

import com.aps.domain.model.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 用户 Mapper：BaseMapper 提供 CRUD，动态条件由 QueryWrapper 在 service 层构造。 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
