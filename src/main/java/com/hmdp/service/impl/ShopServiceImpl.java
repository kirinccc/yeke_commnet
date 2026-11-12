package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.ExceptionConstants;
import com.hmdp.constants.RedisConstants;
import com.hmdp.entity.Shop;
import com.hmdp.exception.BaseException;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ShopMapper shopMapper;

    /**
     * 根据id查询商铺信息，使用互斥锁解决缓存击穿
     * @param id 商铺id
     * @return 商铺信息
     */
    @Override
    public Shop findShopById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;

        // 1 查缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 2 命中真实数据，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 3 命中空串：之前已确认不存在，抛业务异常（防穿透）
        if (shopJson != null) {
            throw new BaseException(ExceptionConstants.SHOP_NOT_EXIST);
        }

        // 4 未命中，尝试获取互斥锁（锁 key 必须与缓存 key 分开）
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        if (!tryLock(lockKey)) {
            // 抢锁失败：短暂等待后重试
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BaseException("查询商铺被中断");
            }
            return findShopById(id);
        }

        // 5 只有抢到锁的线程才进入这里，finally 也只属于持锁者
        try {
            // 5.1 再查一次缓存：可能别的线程刚重建完（double check）
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            if (shopJson != null) {
                throw new BaseException(ExceptionConstants.SHOP_NOT_EXIST);
            }

            // 5.2 查库
            Shop shop = shopMapper.findShopById(id);
            if (shop == null) {
                // 缓存空值，防止缓存穿透
                stringRedisTemplate.opsForValue()
                        .set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                throw new BaseException(ExceptionConstants.SHOP_NOT_EXIST);
            }

            // 5.3 重建缓存
            stringRedisTemplate.opsForValue()
                    .set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

            return shop;

        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询商铺失败，id={}", id, e);
            throw new BaseException("查询商铺失败，请稍后重试");
        } finally {
            // 5.4 只释放自己的锁
            stringRedisTemplate.delete(lockKey);
        }
    }

    /**
     * 根据id更新商铺信息，并采用主动更新策略（先写库，再删缓存）
     * @param shop 商铺数据
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateShopById(Shop shop) {
        // 1 校验id
        if (shop.getId() == null) {
            log.error("商铺id为空");
            throw new BaseException(ExceptionConstants.SHOP_ID_NOT_EXIST);
        }
        // 2 更新数据库
        int rows = shopMapper.updateShopById(shop);
        // 3 校验影响行数
        if (rows == 0) {
            throw new BaseException(ExceptionConstants.UPDATA_SHOP_IS_NULL);
        }
        // 4 删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
    }

    /**
     * 获取互斥锁
     * @param lockKey 锁的key
     * @return 是否获取成功
     */
    private boolean tryLock(String lockKey) {
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
}
