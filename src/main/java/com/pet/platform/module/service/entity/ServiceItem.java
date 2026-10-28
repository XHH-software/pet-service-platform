package com.pet.platform.module.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pet.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("service_item")
public class ServiceItem extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String image;

    private Long categoryId;

    private BigDecimal price;

    /** 服务时长（分钟） */
    private Integer duration;

    /** 0不限 1小型 2中型 3大型 */
    private Integer petSizeLimit;

    private Integer dailyLimit;

    private String description;

    private Integer status;

    @TableField(exist = false)
    private String categoryName;
}
