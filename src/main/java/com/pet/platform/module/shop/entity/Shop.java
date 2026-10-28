package com.pet.platform.module.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.pet.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("shop")
public class Shop extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String logo;

    private String phone;

    private String address;

    /** 1营业中 0休息中 */
    private Integer businessStatus;

    /** 管理端使用 status 字段，做兼容别名 */
    @TableField(exist = false)
    private Integer status;

    @TableField(exist = false)
    private List<ShopBusinessHour> businessHours;
}
