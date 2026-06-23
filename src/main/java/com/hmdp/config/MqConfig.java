package com.hmdp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MqConfig {

    public static final String SECKILL_EXCHANGE = "seckill.exchange";
    public static final String SECKILL_QUEUE = "seckill.queue";
    public static final String SECKILL_ROUTING_KEY = "seckill.queue";

    public static final String SECKILL_DLX = "seckill.dlx";
    public static final String SECKILL_DLQ = "seckill.dlq";
    public static final String SECKILL_DLQ_ROUTING_KEY = "seckill.dlq";

    @Value("${seckill.mq.concurrent-consumers:4}")
    private Integer concurrentConsumers;

    @Value("${seckill.mq.max-concurrent-consumers:8}")
    private Integer maxConcurrentConsumers;

    @Value("${seckill.mq.prefetch:20}")
    private Integer prefetch;

    @Bean
    public TopicExchange seckillExchange() {
        return new TopicExchange(SECKILL_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillQueue() {
        return QueueBuilder.durable(SECKILL_QUEUE)
                .deadLetterExchange(SECKILL_DLX)
                .deadLetterRoutingKey(SECKILL_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding seckillBinding() {
        return BindingBuilder.bind(seckillQueue()).to(seckillExchange()).with(SECKILL_ROUTING_KEY);
    }

    @Bean
    public DirectExchange seckillDeadLetterExchange() {
        return new DirectExchange(SECKILL_DLX, true, false);
    }

    @Bean
    public Queue seckillDeadLetterQueue() {
        return QueueBuilder.durable(SECKILL_DLQ).build();
    }

    @Bean
    public Binding seckillDeadLetterBinding() {
        return BindingBuilder.bind(seckillDeadLetterQueue())
                .to(seckillDeadLetterExchange())
                .with(SECKILL_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        template.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("RabbitMQ message publish failed, correlationData={}, cause={}", correlationData, cause);
            }
        });
        template.setReturnsCallback(returned -> log.error(
                "RabbitMQ message returned, exchange={}, routingKey={}, replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()
        ));
        return template;
    }

    @Bean("seckillListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory seckillListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(concurrentConsumers);
        factory.setMaxConcurrentConsumers(maxConcurrentConsumers);
        factory.setPrefetchCount(prefetch);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }
}
