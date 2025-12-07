package com.videowebsite.VideoWebsite.Entities;

public class PromoCode {
    private String code;
    private String type; // "flat" or "percent"
    private int amount; // amount for flat or percent value
    private String appliesToCourseId; // null means global
    private Integer maxUses; // null means unlimited
    private Integer uses; // current uses
    private Boolean singleUsePerUser;
    private Long expiresAt; // epoch millis
    private Boolean active;
    private String influencerName;
    private Long createdAt;

    public PromoCode() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }

    public String getAppliesToCourseId() { return appliesToCourseId; }
    public void setAppliesToCourseId(String appliesToCourseId) { this.appliesToCourseId = appliesToCourseId; }

    public Integer getMaxUses() { return maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }

    public Integer getUses() { return uses; }
    public void setUses(Integer uses) { this.uses = uses; }

    public Boolean getSingleUsePerUser() { return singleUsePerUser; }
    public void setSingleUsePerUser(Boolean singleUsePerUser) { this.singleUsePerUser = singleUsePerUser; }

    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public String getInfluencerName() { return influencerName; }
    public void setInfluencerName(String influencerName) { this.influencerName = influencerName; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }
}
