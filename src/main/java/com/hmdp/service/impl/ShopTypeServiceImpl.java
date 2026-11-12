package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.constants.RedisConstants;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    private ShopTypeMapper shopTypeMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /*
    * 为shopType添加缓存
    * @return 商铺类型列表
    *
    * */
    @Override
    public List<ShopType> getShopTypeList() {
        //1.从Redis里面查询商铺类型列表
        String shopTypeListStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOPTYPE_KEY);
        //2.判断Redis返回的商铺类型列表是否为空
        if(StrUtil.isNotBlank(shopTypeListStr)){
            //3.存在直接返回商铺类型列表
            List<ShopType> shopTypeList = JSONUtil.toList(shopTypeListStr, ShopType.class);
            return shopTypeList;
        }
        //4.Redis里面不存在就去查询数据库
        List<ShopType> shopTypeList = shopTypeMapper.findShopTypeList();
        //5.校验数据库返回的结果是否为空
        if(shopTypeList == null){
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOPTYPE_KEY, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Collections.emptyList();
        }
        //5.将查询出来的商铺类型列表添加到Redis里面
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOPTYPE_KEY, JSONUtil.toJsonStr(shopTypeList), RedisConstants.CACHE_SHOPTYPE_TTL, TimeUnit.MINUTES);
        return shopTypeList;
    }
}
