package com.pet.platform.module.review.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pet.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review")
public class Review extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long appointmentId;

    private Long customerId;

    private Long staffId;

    /** 1-5 分 */
    private Integer score;

    private String content;

    @JsonIgnore
    @TableField("images")
    private String imagesJson;

    private String replyContent;

    private LocalDateTime replyTime;

    // ===== 展示字段 =====
    @TableField(exist = false)
    private List<String> images;

    @TableField(exist = false)
    private Integer rating;

    @TableField(exist = false)
    private String customerName;

    @TableField(exist = false)
    private String petName;

    @TableField(exist = false)
    private String staffName;
}
