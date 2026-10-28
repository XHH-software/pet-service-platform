package com.pet.platform.module.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pet.platform.module.appointment.entity.AppointmentSlot;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

public interface AppointmentSlotMapper extends BaseMapper<AppointmentSlot> {

    @Select("select count(*) from appointment_slot where appointment_date = #{date}")
    long countByDate(@Param("date") LocalDate date);
}
