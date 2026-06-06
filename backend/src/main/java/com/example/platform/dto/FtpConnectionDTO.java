package com.example.platform.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class FtpConnectionDTO {
    @NotBlank(message = "连接名称不能为空")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "主机地址不能为空")
    @Size(max = 255)
    private String host;

    private Integer port;

    @Size(max = 100)
    private String username;

    /**
     * Plain text password. Leave null when updating and keeping the previous password.
     */
    private String password;

    /**
     * When true and the request is an update, the stored password will be cleared.
     */
    private Boolean clearPassword;

    private Boolean passiveMode;

    @Size(max = 32)
    private String encoding;

    @Size(max = 500)
    private String homePath;

    @Size(max = 500)
    private String description;

    private Integer status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getClearPassword() {
        return clearPassword;
    }

    public void setClearPassword(Boolean clearPassword) {
        this.clearPassword = clearPassword;
    }

    public Boolean getPassiveMode() {
        return passiveMode;
    }

    public void setPassiveMode(Boolean passiveMode) {
        this.passiveMode = passiveMode;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getHomePath() {
        return homePath;
    }

    public void setHomePath(String homePath) {
        this.homePath = homePath;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
