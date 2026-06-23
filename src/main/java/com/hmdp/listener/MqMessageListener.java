package com.hmdp.listener;

import com.hmdp.config.MqConfig;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.IVoucherOrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class MqMessageListener {

    @Resource
    private IVoucherOrderService voucherOrderService;

    @RabbitListener(queues = MqConfig.SECKILL_QUEUE, containerFactory = "seckillListenerContainerFactory")
    public void listenSeckillOrder(VoucherOrder voucherOrder) {
        log.debug("Received seckill order message, orderId={}", voucherOrder.getId());
        voucherOrderService.createVoucherOrder(voucherOrder);
    }
}
