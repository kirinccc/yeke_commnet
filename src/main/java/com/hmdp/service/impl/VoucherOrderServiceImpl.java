package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.constants.ExceptionConstants;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.exception.BaseException;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.DistributedLock;
import com.hmdp.utils.RedisGUIDGenerator;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j

public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    private RedisGUIDGenerator redisGUIDGenerator;
    @Autowired
    private VoucherOrderMapper voucherOrderMapper;
    @Autowired
    private SeckillVoucherMapper seckillVoucherMapper;
    @Autowired
    private DistributedLock redisDistributedLock;
    /*
    * 使用乐观锁实现秒杀优惠券下单
    *
    * */
    @Override
    public long seckillVoucher(Long voucherId) {
        //1.查询优惠券是否存在
        SeckillVoucher seckillVoucher = seckillVoucherMapper.selectById(voucherId);
        if(seckillVoucher == null){
            log.error("优惠券不存在:{}",voucherId);
            throw new BaseException(ExceptionConstants.VOUCHER_NOT_EXIST);
        }
        //2.校验下单时间是否在优惠券的抢购时间内
        //2.1 下单时间早于优惠券开始时间
        if(seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())){
            log.error("下单时间早于优惠券开始时间:{}",voucherId);
            throw new BaseException(ExceptionConstants.VOUCHER_NOT_START);
        }
        //2.2 下单时间晚于优惠券结束时间
        if(seckillVoucher.getEndTime().isBefore(LocalDateTime.now())){
            log.error("下单时间晚于优惠券结束时间:{}",voucherId);
            throw new BaseException(ExceptionConstants.VOUCHER_NOT_END);
        }
        //3.查询优惠券库存
        if(seckillVoucher.getStock() <1){
            log.error("优惠券库存不足:{}",voucherId);
            throw new BaseException(ExceptionConstants.VOUCHER_NOT_STOCK);
        }
        Long userId = UserHolder.getUser().getId();
        //获取分布式锁（替代原来的 synchronized 悲观锁）
        if(!redisDistributedLock.tryLock(String.valueOf(userId), 10)){
            log.error("用户:{}重复提交，获取锁失败，优惠券:{}", userId, voucherId);
            throw new BaseException("请勿重复提交，请稍后再试");
        }
        try {
            IVoucherOrderService ivocherOrderService = (IVoucherOrderService)AopContext.currentProxy();
            return ivocherOrderService.createVocherOrder(voucherId);
        } finally {
            //释放分布式锁
            redisDistributedLock.unLock(String.valueOf(userId));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Long createVocherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //4.实现一人限购一张优惠券
        //4.1 查询用户id和优惠券id对应的订单数量
        int count = voucherOrderMapper.findOrderByUserIdAndVoucherId(userId, voucherId);
        //4.2 根据结果判断用户是否购买了一张优惠券
        if(count>0){
            log.error("用户已经购买了一张优惠券:{}", voucherId);
            throw new BaseException(ExceptionConstants.VOUCHER_HAS_BUY);
        }
        //5.使用乐观锁更新优惠券库存
        boolean success = seckillVoucherMapper.updateStockByVoucherId(voucherId);
        if(!success){
            log.error("库存为0，已经全部抢完了:{}", voucherId);
            throw new BaseException(ExceptionConstants.VOUCHER_SOLD_OUT);
        }
        //6.生成订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(userId);
        voucherOrder.setId(redisGUIDGenerator.getNextId("voucher_order"));
        voucherOrder.setStatus(1);
        voucherOrder.setCreateTime(LocalDateTime.now());
        voucherOrderMapper.insert(voucherOrder);
        return voucherOrder.getId();
    }
}
