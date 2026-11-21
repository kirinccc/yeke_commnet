package com.hmdp.service;

import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Shop findShopById(Long id);

    void updateShopById(Shop shop);

    Result queryShopByType(Integer typeId, Integer current, String sortBy, Double x, Double y);
}
