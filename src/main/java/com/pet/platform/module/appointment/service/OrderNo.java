package com.pet.platform.module.appointment.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 订单号生成：AP + yyyyMMdd + 6 位主键。
 *
 * <p>刻意不采用「查当天最大号 + 1」：那是读-改-写，并发下多个事务读到同一个号是必然的
 * （前一事务未提交，后一事务看不见），只能靠唯一索引冲突后重试；而重试时其他事务同样没提交，
 * 于是连续冲突、最终把「下单人数较多」抛给用户。8 个并发下单会有 5 个失败。
 *
 * <p>改成由自增主键派生之后，唯一性由主键天然保证，不存在任何竞态，也就不需要重试。
 * 代价是号段为全局自增（跨天连续），不再是「当日从 0001 开始」。
 */
public final class OrderNo {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private OrderNo() {
    }

    public static String of(LocalDate date, Long id) {
        LocalDate target = date == null ? LocalDate.now() : date;
        return "AP" + target.format(DAY) + String.format("%06d", id);
    }
}
