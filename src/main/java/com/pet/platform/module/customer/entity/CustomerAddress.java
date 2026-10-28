package com.pet.platform.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pet.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("customer_address")
public class CustomerAddress extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;

    private String province;

    private String city;

    private String district;

    private String detail;

    private String contactName;

    private String contactPhone;

    private Boolean isDefault;
}
