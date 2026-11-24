package com.aps.infra.mapper;

import com.aps.domain.model.Resource;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 资源 Mapper（MyBatis-Plus BaseMapper 提供基础 CRUD，复杂 SQL 后续在此扩展）。 */
@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {

    /**
     * 展开工厂/车间节点为可见子树（应用层 ABAC，PF-02 / #8）。
     * 递归 CTE 沿 parent_id 向下展开，UNION 去重避免树中环导致死循环；
     * 全程限定 tenant_id 且排除逻辑删除，与 RLS 租户隔离形成双保险。
     *
     * @param tenantId  租户 id（来自数据范围，禁止信任客户端传参）
     * @param factoryIds 工厂/车间级 resource 节点 id 集合
     * @return 可见资源 id 集合（含给定节点自身及其所有后代）
     */
    @Select("""
            <script>
            WITH RECURSIVE visible_tree(id) AS (
              SELECT id FROM resource
               WHERE tenant_id = #{tenantId} AND is_deleted = FALSE
                 AND id IN
                 <foreach collection="factoryIds" item="fid" open="(" separator="," close=")">#{fid}</foreach>
              UNION
              SELECT r.id FROM resource r JOIN visible_tree v ON r.parent_id = v.id
               WHERE r.tenant_id = #{tenantId} AND r.is_deleted = FALSE
            )
            SELECT id FROM visible_tree
            </script>
            """)
    List<Long> selectVisibleSubtreeIds(@Param("tenantId") Long tenantId,
                                       @Param("factoryIds") List<Long> factoryIds);
}
