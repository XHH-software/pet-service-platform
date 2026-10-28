package com.pet.platform.module.appointment.controller;

import com.pet.platform.common.PageResult;
import com.pet.platform.common.Result;
import com.pet.platform.common.BusinessException;
import com.pet.platform.module.appointment.entity.Appointment;
import com.pet.platform.module.appointment.service.AppointmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/appointment")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService appointmentService;

    @GetMapping("/page")
    public Result<PageResult<Appointment>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String appointmentDate,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long staffId,
            @RequestParam(required = false) String keyword) {
        return Result.ok(appointmentService.pageForAdmin(page, pageSize, startDate, endDate,
                status, staffId, keyword, parseDate(appointmentDate)));
    }

    /**
     * 工作台“今日预约”传的是 today/今天 关键字，其余场景是 yyyy-MM-dd。
     */
    private LocalDate parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String v = value.trim();
        if ("today".equalsIgnoreCase(v) || "今天".equals(v)) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(v);
        } catch (Exception e) {
            throw new BusinessException("日期格式应为 yyyy-MM-dd");
        }
    }

    @GetMapping("/calendar")
    public Result<List<Map<String, Object>>> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(appointmentService.calendar(date));
    }

    @GetMapping("/{id}")
    public Result<Appointment> detail(@PathVariable Long id) {
        return Result.ok(appointmentService.detailForAdmin(id));
    }

    @PostMapping
    public Result<Map<String, Object>> create(@RequestBody Appointment appointment) {
        return Result.ok(appointmentService.create(appointment, null, true));
    }

    @PostMapping("/{id}/checkin")
    public Result<Void> checkin(@PathVariable Long id) {
        appointmentService.checkin(id);
        return Result.ok();
    }

    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        appointmentService.complete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestBody(required = false) Map<String, String> body) {
        appointmentService.cancelByAdmin(id, body == null ? null : body.get("cancelReason"));
        return Result.ok();
    }
}
