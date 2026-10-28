package com.pet.platform.module.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pet.platform.module.schedule.entity.Schedule;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

public interface ScheduleMapper extends BaseMapper<Schedule> {

    /**
     * 乐观锁：已约数 +1，版本号不匹配则更新失败。
     */
    @Update("update schedule set booked_count = booked_count + 1, version = version + 1, update_time = now() " +
            "where id = #{id} and version = #{version}")
    int increaseBooked(@Param("id") Long id, @Param("version") Integer version);

    @Update("update schedule set booked_count = greatest(booked_count - 1, 0), version = version + 1, update_time = now() " +
            "where id = #{id} and version = #{version}")
    int decreaseBooked(@Param("id") Long id, @Param("version") Integer version);

    /**
     * 当天所有可约排班的 30 分钟时段总容量。
     */
    @Select("select ifnull(sum(time_to_sec(timediff(end_time, start_time)) / 1800), 0) " +
            "from schedule where schedule_date = #{date} and status = 1")
    Integer capacitySlots(@Param("date") LocalDate date);
}
