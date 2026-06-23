package com.hmdp.config;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class SentinelFallback {

    private SentinelFallback() {}

    // blockHandler：被 Sentinel 限流/降级时调用
    public static Result seckillBlockHandler(Long voucherId, BlockException ex) {
        log.warn("秒杀接口被限流，voucherId={}", voucherId);
        return Result.fail("排队中，请稍后重试");
    }

    // fallback：业务异常时调用
    public static Result seckillFallback(Long voucherId, Throwable t) {
        log.error("秒杀接口异常，voucherId={}", voucherId, t);
        return Result.fail("系统繁忙，请稍后重试");
    }
}
