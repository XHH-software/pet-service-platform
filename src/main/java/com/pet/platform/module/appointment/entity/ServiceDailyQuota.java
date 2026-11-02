package com.pet.platform.module.appointment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 服务项目 / 套餐的「每日名额令牌」。
 *
 * <p>每一行代表某个服务在某一天被占用掉的一个名额序号（seq）。
 * (service_item_id, quota_date, seq) 与 (package_id, quota_date, seq) 上各有一个唯一索引，
 * 因此「抢名额」这件事被交给数据库的唯一约束完成：并发下同一个 seq 只会有一个事务插入成功，
 * 失败者顺延到下一个 seq，全部耗尽即判定当日名额已满。
 *
 * <p>相比「先 count 再 insert」，这种写法没有 TOCTOU 窗口；相比 Redis 计数器，
 * 它不会因为缓存丢失或事务回滚而与真实订单数漂移——订单回滚，令牌行一起回滚。
 */
@Data
@TableName("service_daily_quota")
public class ServiceDailyQuota {

    @TableId(type = IdType.AUTO)
    private Long id;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate quotaDate;

    /** 服务项目 ID；与 packageId 二者必有一个非空 */
    private Long serviceItemId;

    /** 套餐 ID；与 serviceItemId 二者必有一个非空 */
    private Long packageId;

    /** 当天第几个名额，从 1 开始，上限为该服务/套餐的 daily_limit */
    private Integer seq;

    /** 占用该名额的预约 ID，取消预约时按它整批回收 */
    private Long appointmentId;

    private LocalDateTime createTime;
}
