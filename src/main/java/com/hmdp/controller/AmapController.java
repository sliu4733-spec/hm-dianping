package com.hmdp.controller;

import com.hmdp.amap.AmapPoiClient;
import com.hmdp.dto.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/amap")
public class AmapController {

    @Resource
    private AmapPoiClient amapPoiClient;

    @GetMapping("/around")
    public Result around(@RequestParam Double lat,
                         @RequestParam Double lng,
                         @RequestParam(defaultValue = "food") String keyword,
                         @RequestParam(defaultValue = "10") Double radiusKm) {
        if (!amapPoiClient.enabled()) {
            return Result.fail("高德 Web 服务 Key 未配置，请设置环境变量 AMAP_WEB_KEY");
        }
        return Result.ok(amapPoiClient.searchAround(lat, lng, radiusKm, keyword));
    }
}
