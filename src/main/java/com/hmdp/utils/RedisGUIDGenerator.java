package com.hmdp.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * @author Kirin_Chen
 * @date 2026/11/12 14:42
 * @一边写一抽方法，别都往main里面塞
 */
@Component
@Slf4j
public class RedisGUIDGenerator {
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final String KEY_PREFIX = "guid:";
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    public long getNextId(String keyPrefix){
        //1.获取时间戳
        long now = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
        long diff = now - BEGIN_TIMESTAMP;
        if(diff < 0){
            throw new RuntimeException("服务器时间校准后，不能生成ID");
        }
        //2.获取序列号
        //2.1.获取当天日期，统一格式为yyyy:MM:dd
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //2.2 利用Redis自增获取序列号
        Long count = stringRedisTemplate.opsForValue().increment(KEY_PREFIX +keyPrefix+ date);
        stringRedisTemplate.expire(KEY_PREFIX +keyPrefix+ date, 2, TimeUnit.DAYS);
        //3.拼接时间戳+序列号返回
        return diff << COUNT_BITS | count;
    }
}