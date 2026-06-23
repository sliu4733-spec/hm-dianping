package com.hmdp.config;

import com.hmdp.handler.CanalStockHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.InetSocketAddress;

@Slf4j
@Configuration
public class CanalClientConfig {

    @Value("${canal.server.host:127.0.0.1}")
    private String canalHost;

    @Value("${canal.server.port:11111}")
    private int canalPort;

    @Value("${canal.destination:example}")
    private String destination;

    @Value("${canal.subscribe.filter:hmdp\\.tb_seckill_voucher,hmdp\\.tb_voucher_order}")
    private String subscribeFilter;

    @Value("${canal.enabled:true}")
    private boolean canalEnabled;

    @Bean(initMethod = "start", destroyMethod = "stop")
    public CanalStockHandler canalStockHandler(StringRedisTemplate stringRedisTemplate) {
        CanalStockHandler handler = new CanalStockHandler();
        handler.setStringRedisTemplate(stringRedisTemplate);
        handler.setCanalServer(new InetSocketAddress(canalHost, canalPort));
        handler.setDestination(destination);
        handler.setSubscribeFilter(subscribeFilter);
        handler.setEnabled(canalEnabled);
        return handler;
    }
}
