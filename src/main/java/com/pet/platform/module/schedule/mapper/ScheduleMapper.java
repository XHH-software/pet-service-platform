package com.pet.platform.module.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pet.platform.module.schedule.entity.Schedule;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

public interface ScheduleMapper extends BaseMapper<Schedule> {

    /**
     * 已约数 +1。
     *
     * <p>{@code booked_count = booked_count + 1} 本身就是原子的：InnoDB 会锁住这一行再自增，
     * 读-改-写之间没有窗口，因此不需要 version 条件。
     *
     * <p>早先用的是「读 version → 带 version 更新 → 失败重试」，但在 MySQL 默认的
     * REPEATABLE_READ 下，同一事务内的一致性读共用首次快照，重试读到的仍是旧 version，
     * 5 次重试必然全败，计数静默少记。改成单条 UPDATE 后该问题不复存在。
     */
    @Update("update schedule set booked_count = booked_count + 1, version = version + 1, update_time = now() " +
            "where id = #{id}")
    int increaseBooked(@Param("id") Long id);

    @Update("update schedule set booked_count = greatest(booked_count - 1, 0), version = version + 1, update_time = now() " +
            "where id = #{id}")
    int decreaseBooked(@Param("id") Long id);

    /**
     * 当天所有可约排班的 30 分钟时段总容量。
     */
    @Select("select ifnull(sum(time_to_sec(timediff(end_time, start_time)) / 1800), 0) " +
            "from schedule where schedule_date = #{date} and status = 1")
    Integer capacitySlots(@Param("date") LocalDate date);
}
