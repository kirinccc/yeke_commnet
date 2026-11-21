package com.hmdp;

import com.hmdp.constants.RedisConstants;
import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private IShopService shopService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /*
    * GEO数据预热：把tb_shop的坐标按类型导入Redis，一个类型一个GEO key
    * GEOADD对同member是覆盖写，幂等，可重复执行
    * */
    @Test
    void loadShopGeoData() {
        //查询全部商铺
        List<Shop> shops = shopService.list();
        //按类型分组
        Map<Long, List<Shop>> shopMap = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        //逐组批量GEOADD：member=shopId，坐标=(x经度,y纬度)
        for (Map.Entry<Long, List<Shop>> entry : shopMap.entrySet()) {
            String key = RedisConstants.SHOP_GEO_KEY + entry.getKey();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(entry.getValue().size());
            for (Shop shop : entry.getValue()) {
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }
}