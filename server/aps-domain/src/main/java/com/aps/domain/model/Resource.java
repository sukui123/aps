package com.aps.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 资源实体（技术方案 §3.2 资源域）。自关联树，含版本/快照/审计通用字段。
 * 本类为示例切片，验证 domain → infra → api 分层可跑通。
 * 表名由 MyBatis-Plus 命名策略映射为 resource（不必在领域层引入框架注解）。
 */
public class Resource {

    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    /** 车间/工作中心/设备/线体/模具/班组 */
    private String type;
    private Long parentId;
    /** 能力标签（PG JSONB，骨架阶段用 JSON 文本承载） */
    private String abilityTags;
    private Integer parallelism;
    private BigDecimal efficiencyFactor;
    private Boolean enabled;
    private Instant validFrom;
    private Instant validTo;

    /** 乐观锁版本（技术方案 §1.7 并发防重；后续迭代可在 infra 侧启用 MP @Version 插件）。 */
    private Long version;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    private Boolean isDeleted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getAbilityTags() {
        return abilityTags;
    }

    public void setAbilityTags(String abilityTags) {
        this.abilityTags = abilityTags;
    }

    public Integer getParallelism() {
        return parallelism;
    }

    public void setParallelism(Integer parallelism) {
        this.parallelism = parallelism;
    }

    public BigDecimal getEfficiencyFactor() {
        return efficiencyFactor;
    }

    public void setEfficiencyFactor(BigDecimal efficiencyFactor) {
        this.efficiencyFactor = efficiencyFactor;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Instant validFrom) {
        this.validFrom = validFrom;
    }

    public Instant getValidTo() {
        return validTo;
    }

    public void setValidTo(Instant validTo) {
        this.validTo = validTo;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }
}
