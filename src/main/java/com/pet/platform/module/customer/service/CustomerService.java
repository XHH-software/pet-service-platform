package com.pet.platform.module.customer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pet.platform.common.PageResult;
import com.pet.platform.module.customer.entity.Customer;
import com.pet.platform.module.customer.entity.Pet;
import com.pet.platform.module.customer.mapper.CustomerMapper;
import com.pet.platform.module.customer.mapper.PetMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerMapper customerMapper;
    private final PetMapper petMapper;

    public PageResult<Customer> page(int page, int pageSize, String keyword) {
        Page<Customer> p = new Page<>(page, pageSize);
        LambdaQueryWrapper<Customer> wrapper = Wrappers.lambdaQuery();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Customer::getNickname, keyword).or().like(Customer::getPhone, keyword));
        }
        wrapper.orderByDesc(Customer::getId);
        customerMapper.selectPage(p, wrapper);

        List<Customer> records = p.getRecords();
        records.forEach(c -> c.setName(c.getNickname()));
        if (!records.isEmpty()) {
            List<Pet> pets = petMapper.selectList(Wrappers.<Pet>lambdaQuery()
                    .in(Pet::getCustomerId, records.stream().map(Customer::getId).collect(Collectors.toList())));
            Map<Long, Long> counts = pets.stream()
                    .collect(Collectors.groupingBy(Pet::getCustomerId, Collectors.counting()));
            records.forEach(c -> c.setPetCount(counts.getOrDefault(c.getId(), 0L).intValue()));
        }
        return PageResult.of(p);
    }

    public Customer getById(Long id) {
        return customerMapper.selectById(id);
    }
}
