package com.hmdp.controller;

import com.hmdp.ai.orchestrator.CustomerServiceOrchestrator;
import com.hmdp.dto.Result;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/ai")
public class AiCustomerController {

    @Resource
    private CustomerServiceOrchestrator orchestrator;

    @PostMapping("/chat")
    public Result chat(@RequestBody Map<String, Object> body) {
        try {
            String message = body.get("message") == null ? "" : body.get("message").toString().trim();
            if (message.isEmpty()) {
                return Result.fail("消息不能为空");
            }
            if (UserHolder.getUser() == null) {
                return Result.fail("请先登录后再使用智能客服");
            }

            Long userId = UserHolder.getUser().getId();
            Double lat = toDouble(body.get("lat"));
            Double lng = toDouble(body.get("lng"));
            String city = body.get("city") == null ? "" : body.get("city").toString();
            Boolean detected = toBoolean(body.get("detected"));
            return Result.ok(orchestrator.handle(userId, message, lat, lng, city, detected));
        } catch (Exception e) {
            log.error("AI customer service failed", e);
            return Result.ok("抱歉，智能客服暂时不可用，请稍后再试。");
        }
    }

    private Double toDouble(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value == null) {
            return false;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
