package com.pet.platform.module.staff.controller;

import com.pet.platform.common.PageResult;
import com.pet.platform.common.Result;
import com.pet.platform.module.staff.entity.Staff;
import com.pet.platform.module.staff.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/staff")
@RequiredArgsConstructor
public class StaffController {

    private final StaffService staffService;

    @GetMapping("/page")
    public Result<PageResult<Staff>> page(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize,
                                          @RequestParam(required = false) String name,
                                          @RequestParam(required = false) Integer position,
                                          @RequestParam(required = false) Integer status) {
        return Result.ok(staffService.page(page, pageSize, name, position, status));
    }

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(staffService.options());
    }

    @GetMapping("/{id}")
    public Result<Staff> detail(@PathVariable Long id) {
        return Result.ok(staffService.detail(id));
    }

    @PostMapping
    public Result<Void> create(@RequestBody Staff staff) {
        staffService.create(staff);
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@RequestBody Staff staff) {
        staffService.update(staff);
        return Result.ok();
    }

    @PostMapping("/status/{status}")
    public Result<Void> updateStatus(@PathVariable Integer status, @RequestParam Long id) {
        staffService.updateStatus(id, status);
        return Result.ok();
    }
}
