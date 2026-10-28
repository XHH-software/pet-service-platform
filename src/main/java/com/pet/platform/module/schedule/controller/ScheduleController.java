package com.pet.platform.module.schedule.controller;

import com.pet.platform.common.Result;
import com.pet.platform.module.schedule.entity.Schedule;
import com.pet.platform.module.schedule.service.ScheduleService;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/schedule")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping("/week")
    public Result<List<Map<String, Object>>> week(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate) {
        return Result.ok(scheduleService.week(startDate));
    }

    @PostMapping("/batch")
    public Result<Void> batch(@RequestBody BatchRequest request) {
        scheduleService.batch(request.getStaffIds(), request.getStartDate(), request.getEndDate(),
                request.getStartTime(), request.getEndTime(), request.getStatus());
        return Result.ok();
    }

    /**
     * 管理端“单日排班”弹窗：新增时 scheduleId 为空，前端会调用 PUT /admin/schedule/create。
     */
    @PutMapping("/create")
    public Result<Void> create(@RequestBody SingleRequest request) {
        scheduleService.save(request.getStaffId(), request.getDate(), request.getStartTime(),
                request.getEndTime(), request.getStatus());
        return Result.ok();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Schedule schedule) {
        scheduleService.update(id, schedule);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return Result.ok();
    }

    @PostMapping("/copy")
    public Result<Void> copy(@RequestBody CopyRequest request) {
        scheduleService.copyPreviousWeek(request.getStartDate());
        return Result.ok();
    }

    @Data
    public static class BatchRequest {
        private List<Long> staffIds;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate endDate;
        @JsonFormat(pattern = "HH:mm")
        private LocalTime startTime;
        @JsonFormat(pattern = "HH:mm")
        private LocalTime endTime;
        private Integer status;
    }

    @Data
    public static class SingleRequest {
        private Long staffId;
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate date;
        @JsonFormat(pattern = "HH:mm")
        private LocalTime startTime;
        @JsonFormat(pattern = "HH:mm")
        private LocalTime endTime;
        private Integer status;
    }

    @Data
    public static class CopyRequest {
        @JsonFormat(pattern = "yyyy-MM-dd")
        private LocalDate startDate;
    }
}
