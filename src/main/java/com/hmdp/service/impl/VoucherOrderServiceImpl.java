package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.hmdp.config.MqConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

/**
 * Voucher order service.
 *
 * <p>Seckill flow:
 * Redis Lua validates stock and one-order-per-user first, then RabbitMQ writes
 * the order to MySQL asynchronously. MySQL unique index is the final safeguard.</p>
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private Cache<Long, Boolean> stockSoldOutCache;

    @Override
    public Result seckillVoucher(Long voucherId) {
        if (Boolean.TRUE.equals(stockSoldOutCache.getIfPresent(voucherId))) {
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
        long orderId = redisIdWorker.nextId("order");

        Long scriptResult = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        if (scriptResult == null) {
            log.error("Seckill Lua script returned null, voucherId={}, userId={}", voucherId, userId);
            return Result.fail("系统繁忙，请稍后再试");
        }

        int resultCode = scriptResult.intValue();
        if (resultCode == 1) {
            stockSoldOutCache.put(voucherId, true);
            return Result.fail("库存不足");
        }
        if (resultCode == 2) {
            return Result.fail("不能重复下单");
        }

        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);

        rabbitTemplate.convertAndSend(MqConfig.SECKILL_EXCHANGE, MqConfig.SECKILL_ROUTING_KEY, voucherOrder);
        return Result.ok(orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);

        boolean locked = lock.tryLock();
        if (!locked) {
            log.warn("Duplicate seckill order blocked by Redisson, userId={}, voucherId={}", userId, voucherId);
            return;
        }

        try {
            long count = query()
                    .eq("user_id", userId)
                    .eq("voucher_id", voucherId)
                    .count();
            if (count > 0) {
                log.warn("Duplicate seckill order blocked by DB query, userId={}, voucherId={}", userId, voucherId);
                return;
            }

            boolean stockDeducted = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)
                    .update();
            if (!stockDeducted) {
                log.warn("MySQL stock deduction failed, voucherId={}, orderId={}", voucherId, voucherOrder.getId());
                return;
            }

            save(voucherOrder);
        } catch (DuplicateKeyException e) {
            log.warn("Duplicate seckill order blocked by unique index, userId={}, voucherId={}", userId, voucherId);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
