package com.hmdp.utils;

public interface DistributedLock {
     boolean tryLock(String userId, long expire);
     void unLock(String userId);
}
