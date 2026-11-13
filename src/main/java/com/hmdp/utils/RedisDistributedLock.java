package com.hmdp.utils;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author Kirin_Chen
 * @date 2026/11/13 15:21
 * @一边写一抽方法，别都往main里面塞
 */
@Component
@Slf4j
@Data
public class RedisDistributedLock implements DistributedLock{
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    private final String REDIS_SETNX_KEY="redis:lock:";
    private final String REDIS_SETNX_VALUE= UUID.randomUUID().toString()+"-";

    /*
    * 获取锁
    * @param key 锁的key
    * @param value 锁的value
    * @param expire 锁的过期时间
    * @return 是否获取锁成功
    * */
    @Override
    public boolean tryLock(String userId, long expire) {
        //获取当前线程的id作为Redis分布式锁的线程标识，避免误删
        long threadId = Thread.currentThread().getId();
        return stringRedisTemplate.opsForValue().setIfAbsent(REDIS_SETNX_KEY+userId,REDIS_SETNX_VALUE+threadId,expire, TimeUnit.SECONDS);
    }

    /*
     * 删除锁
     * */
    @Override
    public void unLock(String userId) {
        long threadId = Thread.currentThread().getId();
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(REDIS_SETNX_KEY+userId),
                REDIS_SETNX_VALUE+threadId);
    }



}