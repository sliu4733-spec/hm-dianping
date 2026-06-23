package com.hmdp.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class SentinelConfig {

    // 注册 Sentinel AOP 切面，让 @SentinelResource 生效
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }

    @PostConstruct
    public void initFlowRules() {
        List<FlowRule> rules = new ArrayList<>();

        FlowRule controllerRule = new FlowRule();
        controllerRule.setResource("seckillController");
        controllerRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        controllerRule.setCount(10000);
        rules.add(controllerRule);

        FlowRuleManager.loadRules(rules);
    }
}
