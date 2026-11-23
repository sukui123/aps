package com.aps.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/** 集成数据源配置（V2.integration_config）。凭据密文永不序列化/回显。 */
public class IntegrationConfig {

    private Long id;
    private Long tenantId;
    private String code;
    private String name;
    /** REST / DB_VIEW / FILE */
    private String type;
    /** REST 地址、视图名或文件路径（非敏感）。 */
    private String endpoint;
    @JsonIgnore
    private String credentialsCipher;
    /** NONE / AK_SK */
    private String signatureType;
    /** JSONB 文本（IP 白名单）。 */
    private String ipAllowlist;
    private String cronExpr;
    private Boolean enabled;
    private Instant createdAt;
    private String createdBy;
    private Instant updatedAt;
    private String updatedBy;
    private Long version;
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

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    @JsonIgnore
    public String getCredentialsCipher() {
        return credentialsCipher;
    }

    public void setCredentialsCipher(String credentialsCipher) {
        this.credentialsCipher = credentialsCipher;
    }

    /** 仅用于接口回显的掩码字段；密文本身由 @JsonIgnore 永不序列化。 */
    public String getCredentialsMasked() {
        return (credentialsCipher == null || credentialsCipher.isEmpty()) ? null : "****";
    }

    public String getSignatureType() {
        return signatureType;
    }

    public void setSignatureType(String signatureType) {
        this.signatureType = signatureType;
    }

    public String getIpAllowlist() {
        return ipAllowlist;
    }

    public void setIpAllowlist(String ipAllowlist) {
        this.ipAllowlist = ipAllowlist;
    }

    public String getCronExpr() {
        return cronExpr;
    }

    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Boolean getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Boolean isDeleted) {
        this.isDeleted = isDeleted;
    }
}
