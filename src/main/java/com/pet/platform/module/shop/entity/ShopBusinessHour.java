package com.pet.platform.module.shop.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalTime;

@Data
@TableName("shop_business_hour")
public class ShopBusinessHour {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shopId;

    /** 1周一 7周日 */
    private Integer dayOfWeek;

    private Boolean isRest;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime openTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime closeTime;
}
