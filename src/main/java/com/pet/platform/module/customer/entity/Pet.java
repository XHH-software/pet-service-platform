package com.pet.platform.module.customer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.pet.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pet")
public class Pet extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long customerId;

    private String name;

    private String avatar;

    /** 1狗 2猫 3其他 */
    private Integer species;

    private String breed;

    /** 1公 2母 */
    private Integer gender;

    private BigDecimal weight;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate birthday;

    /** 管理端按 0/1 展示，小程序端可传 true/false 或 0/1 */
    @JsonFormat(shape = JsonFormat.Shape.NUMBER)
    private Boolean neutered;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate vaccineExpiryDate;

    /** 管理端字段别名：vaccineExpiry */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField(exist = false)
    private LocalDate vaccineExpiry;

    private String allergies;

    private String remark;

    @TableField(exist = false)
    private Customer customer;

    @TableField(exist = false)
    private String customerNickname;

    @TableField(exist = false)
    private String customerPhone;

    @TableField(exist = false)
    private List<Map<String, Object>> appointments;
}
