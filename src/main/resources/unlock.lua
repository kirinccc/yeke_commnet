-- 这里的 KEYS[1] 就是锁的key，这里的ARGV[1] 就是当前线程标示
-- 获取锁中的标示，判断是否与当前线程标示一致
if(redis.call('get',keys[1])==argv[1])then
    -- 删除锁
    return redis.call('del',keys[1])
else
    return 0
end