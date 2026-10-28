package com.pet.platform.module.appointment.entity;

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
import java.time.LocalTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("appointment")
public class Appointment extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long customerId;

    private Long petId;

    private Long staffId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate appointmentDate;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @JsonFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private BigDecimal totalAmount;

    private BigDecimal paidAmount;

    /** 0未付 1已付 2已退 */
    private Integer payStatus;

    /** 1待支付 2待服务 3服务中 4已取消 5已完成 6已评价 */
    private Integer status;

    /** 1客户下单 2商家代下单 */
    private Integer source;

    private String cancelReason;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime payTime;

    // ===== 展示字段 =====
    @TableField(exist = false)
    private String customerName;

    @TableField(exist = false)
    private String customerPhone;

    @TableField(exist = false)
    private String petName;

    @TableField(exist = false)
    private Integer petSpecies;

    @TableField(exist = false)
    private String petBreed;

    @TableField(exist = false)
    private BigDecimal petWeight;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField(exist = false)
    private LocalDate vaccineExpiryDate;

    /** 管理端字段别名：vaccineExpiry（宠物页/详情页使用） */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @TableField(exist = false)
    private LocalDate vaccineExpiry;

    @TableField(exist = false)
    private String staffName;

    @TableField(exist = false)
    private String staffAvatar;

    @TableField(exist = false)
    private Integer staffLevel;

    /** 职级文案：初级 / 中级 / 高级 */
    @TableField(exist = false)
    private String staffLevelLabel;

    @TableField(exist = false)
    private String itemSummary;

    /** 小程序端展示：服务项目名称（多个用、连接） */
    @TableField(exist = false)
    private String serviceItemName;

    /** 小程序端评价页字段别名：serviceName */
    @TableField(exist = false)
    private String serviceName;

    /** 小程序端评价页字段别名：timeSlotName（如 18:00-19:30） */
    @TableField(exist = false)
    private String timeSlotName;

    /** 小程序端展示：服务项目/套餐首图 */
    @TableField(exist = false)
    private String serviceItemIcon;

    /** 服务总时长（分钟） */
    @TableField(exist = false)
    private Integer duration;

    /** 宠物种类文案：狗 / 猫 / 其他 */
    @TableField(exist = false)
    private String petSpeciesLabel;

    /** 是否已评价，小程序端用于显示“去评价”按钮 */
    @TableField(exist = false)
    private Boolean hasReview;

    /** 管理端字段别名：amount（列表/宠物档案预约记录使用） */
    @TableField(exist = false)
    private BigDecimal amount;

    @TableField(exist = false)
    private List<AppointmentItem> items;

    // ===== 下单入参 =====
    @TableField(exist = false)
    private Long serviceItemId;

    @TableField(exist = false)
    private List<Long> serviceItemIds;

    @TableField(exist = false)
    private Long packageId;
}
