package com.pet.platform.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pet.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer")
public class Customer extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String openid;

    private String nickname;

    private String avatar;

    private String phone;

    @TableField(exist = false)
    private Integer petCount;

    /** 管理端字段别名：name（预约日历客户下拉框使用） */
    @TableField(exist = false)
    private String name;
}
