package com.pet.platform.module.staff.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pet.platform.common.AutoFill;
import com.pet.platform.common.BusinessException;
import com.pet.platform.common.PageResult;
import com.pet.platform.module.service.entity.ServiceItem;
import com.pet.platform.module.service.mapper.ServiceItemMapper;
import com.pet.platform.module.staff.entity.Staff;
import com.pet.platform.module.staff.entity.StaffSpecialty;
import com.pet.platform.module.staff.mapper.StaffMapper;
import com.pet.platform.module.staff.mapper.StaffSpecialtyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffMapper staffMapper;
    private final StaffSpecialtyMapper staffSpecialtyMapper;
    private final ServiceItemMapper serviceItemMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResult<Staff> page(int page, int pageSize, String name, Integer position, Integer status) {
        Page<Staff> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Staff> wrapper = Wrappers.lambdaQuery();
        wrapper.like(StringUtils.hasText(name), Staff::getName, name)
                .eq(position != null, Staff::getPosition, position)
                .eq(status != null, Staff::getStatus, status)
                .orderByAsc(Staff::getId);
        staffMapper.selectPage(p, wrapper);
        fillSpecialties(p.getRecords());
        return PageResult.of(p);
    }

    public List<Map<String, Object>> options() {
        List<Staff> staffList = staffMapper.selectList(Wrappers.<Staff>lambdaQuery()
                .eq(Staff::getStatus, 1)
                .orderByAsc(Staff::getPosition)
                .orderByAsc(Staff::getId));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Staff staff : staffList) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", staff.getId());
            item.put("name", staff.getName());
            list.add(item);
        }
        return list;
    }

    public Staff detail(Long id) {
        Staff staff = staffMapper.selectById(id);
        if (staff == null) {
            throw new BusinessException("员工不存在");
        }
        fillSpecialties(Collections.singletonList(staff));
        return staff;
    }

    /**
     * 登录账号默认取手机号，前端新增员工表单没有 username 字段。
     */
    @AutoFill(AutoFill.Type.INSERT)
    @Transactional(rollbackFor = Exception.class)
    public void create(Staff staff) {
        if (!StringUtils.hasText(staff.getPhone())) {
            throw new BusinessException("请输入手机号");
        }
        if (!StringUtils.hasText(staff.getPassword())) {
            throw new BusinessException("请输入密码");
        }
        checkPhoneUnique(staff.getPhone(), null);
        String username = StringUtils.hasText(staff.getUsername()) ? staff.getUsername() : staff.getPhone();
        if (staffMapper.selectCount(Wrappers.<Staff>lambdaQuery().eq(Staff::getUsername, username)) > 0) {
            throw new BusinessException("账号已存在");
        }
        staff.setId(null);
        staff.setUsername(username);
        staff.setPassword(passwordEncoder.encode(staff.getPassword()));
        if (staff.getStatus() == null) {
            staff.setStatus(1);
        }
        staffMapper.insert(staff);
        saveSpecialties(staff.getId(), staff.getSpecialtyItemIds());
    }

    @AutoFill(AutoFill.Type.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    public void update(Staff staff) {
        Staff exist = staffMapper.selectById(staff.getId());
        if (exist == null) {
            throw new BusinessException("员工不存在");
        }
        if (StringUtils.hasText(staff.getPhone())) {
            checkPhoneUnique(staff.getPhone(), staff.getId());
        }
        staff.setPassword(null);
        staff.setUsername(null);
        staffMapper.updateById(staff);
        if (staff.getSpecialtyItemIds() != null) {
            saveSpecialties(staff.getId(), staff.getSpecialtyItemIds());
        }
    }

    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态不合法");
        }
        Staff staff = new Staff();
        staff.setId(id);
        staff.setStatus(status);
        staffMapper.updateById(staff);
    }

    private void checkPhoneUnique(String phone, Long excludeId) {
        LambdaQueryWrapper<Staff> wrapper = Wrappers.<Staff>lambdaQuery().eq(Staff::getPhone, phone);
        if (excludeId != null) {
            wrapper.ne(Staff::getId, excludeId);
        }
        if (staffMapper.selectCount(wrapper) > 0) {
            throw new BusinessException("手机号已被其他员工使用");
        }
    }

    private void saveSpecialties(Long staffId, List<Long> itemIds) {
        staffSpecialtyMapper.delete(Wrappers.<StaffSpecialty>lambdaQuery().eq(StaffSpecialty::getStaffId, staffId));
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        List<Long> distinct = itemIds.stream().filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
        for (Long itemId : distinct) {
            StaffSpecialty specialty = new StaffSpecialty();
            specialty.setStaffId(staffId);
            specialty.setServiceItemId(itemId);
            staffSpecialtyMapper.insert(specialty);
        }
    }

    private void fillSpecialties(List<Staff> staffList) {
        if (staffList == null || staffList.isEmpty()) {
            return;
        }
        Map<Long, Staff> staffMap = staffList.stream().collect(Collectors.toMap(Staff::getId, Function.identity()));
        List<StaffSpecialty> relations = staffSpecialtyMapper.selectList(Wrappers.<StaffSpecialty>lambdaQuery()
                .in(StaffSpecialty::getStaffId, staffMap.keySet()));
        if (relations.isEmpty()) {
            staffList.forEach(s -> s.setSpecialtyItems(Collections.emptyList()));
            return;
        }
        Map<Long, String> itemNames = serviceItemMapper.selectBatchIds(
                        relations.stream().map(StaffSpecialty::getServiceItemId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(ServiceItem::getId, ServiceItem::getName));
        Map<Long, List<Map<String, Object>>> grouped = new HashMap<>();
        for (StaffSpecialty relation : relations) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", relation.getServiceItemId());
            item.put("name", itemNames.getOrDefault(relation.getServiceItemId(), ""));
            grouped.computeIfAbsent(relation.getStaffId(), k -> new ArrayList<>()).add(item);
        }
        staffMap.forEach((id, staff) -> staff.setSpecialtyItems(grouped.getOrDefault(id, Collections.emptyList())));
    }
}
