package com.pet.platform.module.customer.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pet.platform.common.AutoFill;
import com.pet.platform.common.BusinessException;
import com.pet.platform.module.customer.entity.CustomerAddress;
import com.pet.platform.module.customer.mapper.CustomerAddressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final CustomerAddressMapper addressMapper;

    public List<CustomerAddress> list(Long customerId) {
        return addressMapper.selectList(Wrappers.<CustomerAddress>lambdaQuery()
                .eq(CustomerAddress::getCustomerId, customerId)
                .orderByDesc(CustomerAddress::getIsDefault)
                .orderByDesc(CustomerAddress::getId));
    }

    @AutoFill(AutoFill.Type.INSERT)
    @Transactional(rollbackFor = Exception.class)
    public void create(CustomerAddress address, Long customerId) {
        validate(address);
        address.setId(null);
        address.setCustomerId(customerId);
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            clearDefault(customerId);
        }
        addressMapper.insert(address);
    }

    @AutoFill(AutoFill.Type.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    public void update(CustomerAddress address, Long customerId) {
        CustomerAddress exist = addressMapper.selectById(address.getId());
        if (exist == null || !exist.getCustomerId().equals(customerId)) {
            throw new BusinessException("地址不存在");
        }
        validate(address);
        address.setCustomerId(customerId);
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            clearDefault(customerId);
        }
        addressMapper.updateById(address);
    }

    public void delete(Long id, Long customerId) {
        CustomerAddress exist = addressMapper.selectById(id);
        if (exist == null || !exist.getCustomerId().equals(customerId)) {
            throw new BusinessException("地址不存在");
        }
        addressMapper.deleteById(id);
    }

    private void clearDefault(Long customerId) {
        CustomerAddress update = new CustomerAddress();
        update.setIsDefault(false);
        addressMapper.update(update, Wrappers.<CustomerAddress>lambdaUpdate()
                .eq(CustomerAddress::getCustomerId, customerId)
                .eq(CustomerAddress::getIsDefault, true));
    }

    private void validate(CustomerAddress address) {
        if (!StringUtils.hasText(address.getDetail())) {
            throw new BusinessException("请输入详细地址");
        }
        if (!StringUtils.hasText(address.getContactName())) {
            throw new BusinessException("请输入联系人");
        }
        if (!StringUtils.hasText(address.getContactPhone())) {
            throw new BusinessException("请输入联系电话");
        }
    }
}
