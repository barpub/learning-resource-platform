package com.example.platform.dto;

import java.math.BigDecimal;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class DanmakuDTO {

    @NotBlank(message = "弹幕内容不能为空")
    @Size(max = 100, message = "弹幕不能超过 100 个字符")
    private String content;

    @NotNull(message = "时间不能为空")
    @DecimalMin(value = "0.0", message = "时间必须 >= 0")
    private BigDecimal timeSeconds;

    /** scroll / top / bottom */
    private String type;

    /** CSS color string. Falls back to white when null. */
    private String color;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public BigDecimal getTimeSeconds() { return timeSeconds; }
    public void setTimeSeconds(BigDecimal timeSeconds) { this.timeSeconds = timeSeconds; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
