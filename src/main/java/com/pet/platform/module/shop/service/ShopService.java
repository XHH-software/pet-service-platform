package com.pet.platform.module.shop.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.pet.platform.common.AutoFill;
import com.pet.platform.common.BusinessException;
import com.pet.platform.module.shop.entity.Shop;
import com.pet.platform.module.shop.entity.ShopBusinessHour;
import com.pet.platform.module.shop.mapper.ShopBusinessHourMapper;
import com.pet.platform.module.shop.mapper.ShopMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ShopService {

    private final ShopMapper shopMapper;
    private final ShopBusinessHourMapper hourMapper;

    public Shop get() {
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().orderByAsc(Shop::getId).last("limit 1"));
        if (shop == null) {
            throw new BusinessException("门店信息未初始化");
        }
        shop.setStatus(shop.getBusinessStatus());
        shop.setBusinessHours(hourMapper.selectList(Wrappers.<ShopBusinessHour>lambdaQuery()
                .eq(ShopBusinessHour::getShopId, shop.getId())
                .orderByAsc(ShopBusinessHour::getDayOfWeek)));
        return shop;
    }

    /**
     * 管理端表单提交的是 status，小程序端使用 businessStatus，这里做兼容。
     */
    @AutoFill(AutoFill.Type.UPDATE)
    @Transactional(rollbackFor = Exception.class)
    public void update(Shop input) {
        Shop shop = shopMapper.selectOne(Wrappers.<Shop>lambdaQuery().orderByAsc(Shop::getId).last("limit 1"));
        if (shop == null) {
            throw new BusinessException("门店信息未初始化");
        }
        input.setId(shop.getId());
        Integer status = input.getBusinessStatus() != null ? input.getBusinessStatus() : input.getStatus();
        input.setBusinessStatus(status == null ? shop.getBusinessStatus() : status);
        shopMapper.updateById(input);

        List<ShopBusinessHour> hours = input.getBusinessHours();
        if (hours != null) {
            if (hours.size() != 7) {
                throw new BusinessException("请完整设置一周 7 天的营业时间");
            }
            hourMapper.delete(Wrappers.<ShopBusinessHour>lambdaQuery()
                    .eq(ShopBusinessHour::getShopId, shop.getId()));
            for (int i = 0; i < hours.size(); i++) {
                ShopBusinessHour hour = hours.get(i);
                hour.setId(null);
                hour.setShopId(shop.getId());
                if (hour.getDayOfWeek() == null) {
                    hour.setDayOfWeek(i + 1);
                }
                if (hour.getIsRest() == null) {
                    hour.setIsRest(false);
                }
                hourMapper.insert(hour);
            }
        }
    }
}
