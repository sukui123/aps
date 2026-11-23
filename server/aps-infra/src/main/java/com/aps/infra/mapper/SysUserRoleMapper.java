package com.aps.infra.mapper;

import com.aps.domain.model.SysUserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 用户-角色绑定 Mapper。 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
