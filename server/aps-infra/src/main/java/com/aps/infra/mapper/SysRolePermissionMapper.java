package com.aps.infra.mapper;

import com.aps.domain.model.SysRolePermission;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 角色-权限绑定 Mapper。 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {
}
