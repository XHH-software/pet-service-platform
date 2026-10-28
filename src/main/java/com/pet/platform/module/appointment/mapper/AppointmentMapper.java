package com.pet.platform.module.appointment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pet.platform.module.appointment.entity.Appointment;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AppointmentMapper extends BaseMapper<Appointment> {

    @Select("select count(*) from appointment where appointment_date = #{date} and status <> 4")
    long countByDate(@Param("date") LocalDate date);

    @Select("select ifnull(sum(paid_amount), 0) from appointment where appointment_date = #{date} and status <> 4")
    BigDecimal turnoverByDate(@Param("date") LocalDate date);

    /**
     * 近 30 天技师排名：按预约数倒序，附带平均评分与营业额。
     */
    @Select("select a.staff_id as staffId, s.name as staffName, count(*) as appointmentCount, " +
            "ifnull(sum(a.paid_amount), 0) as turnover, " +
            "ifnull((select round(avg(r.score), 1) from review r where r.staff_id = a.staff_id), 0) as avgScore " +
            "from appointment a join staff s on s.id = a.staff_id " +
            "where a.appointment_date between #{start} and #{end} and a.status <> 4 " +
            "group by a.staff_id, s.name order by appointmentCount desc limit 5")
    List<Map<String, Object>> staffRank(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
