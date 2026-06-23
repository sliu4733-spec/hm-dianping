package com.hmdp.controller;

import com.hmdp.ai.orchestrator.CustomerServiceOrchestrator;
import com.hmdp.dto.Result;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/customer-service")
public class CustomerServiceController {

    @Resource
    private CustomerServiceOrchestrator orchestrator;

    @PostMapping("/chat")
    public Result chat(@RequestParam String message,
                       @RequestParam(required = false) Double lat,
                       @RequestParam(required = false) Double lng,
                       @RequestParam(required = false, defaultValue = "") String city,
                       @RequestParam(required = false, defaultValue = "false") Boolean detected) {
        if (UserHolder.getUser() == null) {
            return Result.fail("请先登录");
        }
        Long userId = UserHolder.getUser().getId();
        return Result.ok(orchestrator.handle(userId, message, lat, lng, city, detected));
    }
}
