package com.pet.platform.module.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pet.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_package")
public class ServicePackage extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String image;

    private Long categoryId;

    private BigDecimal originalPrice;

    private BigDecimal sellingPrice;

    private Integer dailyLimit;

    private String description;

    private Integer status;

    @TableField(exist = false)
    private String categoryName;

    /** 小程序服务详情页字段别名：price = sellingPrice */
    @TableField(exist = false)
    private BigDecimal price;

    /** 套餐总时长（分钟），供小程序详情页展示 */
    @TableField(exist = false)
    private Integer duration;

    @TableField(exist = false)
    private List<PackageDetail> details;
}
