package com.pet.platform.module.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("appointment_item")
public class AppointmentItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appointmentId;

    private Long serviceItemId;

    private Long packageId;

    private String serviceName;

    private BigDecimal price;

    private Integer duration;

    private Integer quantity;

    private BigDecimal subtotal;

    /** 管理端字段别名：itemName */
    @JsonProperty("itemName")
    public String getItemName() {
        return serviceName;
    }

    /** 管理端字段别名：unitPrice */
    @JsonProperty("unitPrice")
    public BigDecimal getUnitPrice() {
        return price;
    }
}
