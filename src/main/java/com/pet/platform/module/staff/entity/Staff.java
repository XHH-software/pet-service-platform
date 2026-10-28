package com.pet.platform.module.staff.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pet.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("staff")
public class Staff extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String username;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    private String avatar;

    private String phone;

    /** 1店长 2美容师 3助理 */
    private Integer position;

    /** 1初级 2中级 3高级 */
    private Integer level;

    /** 1启用 0停用 */
    private Integer status;

    @TableField(exist = false)
    private List<Map<String, Object>> specialtyItems;

    @TableField(exist = false)
    private List<Long> specialtyItemIds;
}
