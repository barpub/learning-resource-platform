package com.example.platform.dto;

/**
 * Publisher/admin-only payload for toggling danmaku on a resource.
 */
public class DanmakuConfigDTO {
    private Boolean enabled;
    /** EVERYONE / LOGGED / OWNER */
    private String permission;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
}
