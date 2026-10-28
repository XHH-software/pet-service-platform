package com.pet.platform.module.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("package_detail")
public class PackageDetail {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long packageId;

    private Long serviceItemId;

    private Integer quantity;

    @TableField(exist = false)
    private String serviceName;

    @TableField(exist = false)
    private Integer duration;

    @TableField(exist = false)
    private BigDecimal price;
}
