package com.hmdp.ai.orchestrator;

import com.hmdp.ai.client.DeepSeekClient;
import com.hmdp.ai.tool.CustomerServiceTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CustomerServiceOrchestrator {

    private static final double DEFAULT_LAT = 30.2741;
    private static final double DEFAULT_LNG = 120.1551;

    private static final String SYSTEM_PROMPT = """
            你是黑马点评的智能客服“小黑”。
            请基于业务数据回答用户，不要编造店铺、订单、库存或优惠券。
            如果用户问附近推荐，要围绕当前位置或当前选择城市回答。
            如果用户只是确认所在城市，请直接确认城市，不要推荐店铺。
            语气自然一点，像真实本地生活客服。
            """;

    @Resource
    private CustomerServiceTool tool;

    @Resource
    private DeepSeekClient deepSeekClient;

    public String handle(Long userId, String message, Double lat, Double lng) {
        return handle(userId, message, lat, lng, "", false);
    }

    public String handle(Long userId, String message, Double lat, Double lng, String city, Boolean detected) {
        String normalizedMessage = message == null ? "" : message.trim();
        double userLat = lat == null ? DEFAULT_LAT : lat;
        double userLng = lng == null ? DEFAULT_LNG : lng;
        String currentCity = city == null || city.isBlank() ? inferCity(userLat, userLng) : city;
        boolean realLocation = Boolean.TRUE.equals(detected);

        if (isLocationQuestion(normalizedMessage)) {
            return locationReply(currentCity, userLat, userLng, realLocation);
        }

        String businessContext = buildBusinessContext(userId, normalizedMessage, userLat, userLng, currentCity);
        String prompt = "当前城市：" + currentCity
                + "\n定位方式：" + (realLocation ? "用户真实定位" : "用户手动选择城市中心点")
                + "\n用户问题：\n" + normalizedMessage
                + "\n\n业务数据：\n" + businessContext;

        String aiResponse = deepSeekClient.chat(SYSTEM_PROMPT, prompt);
        if (aiResponse.startsWith("[AI")) {
            log.warn("AI unavailable, use local fallback: {}", aiResponse);
            return formatFallbackReply(normalizedMessage, businessContext, currentCity, realLocation);
        }
        return aiResponse;
    }

    private String buildBusinessContext(Long userId, String message, double lat, double lng, String city) {
        if (containsAny(message, "订单", "买过", "购买", "抢到", "下单")) {
            return tool.queryUserOrders(userId);
        }

        Long voucherId = extractFirstNumberAfter(message, "券", "优惠券", "库存");
        if (voucherId != null && containsAny(message, "库存", "还有", "剩余", "几张")) {
            return tool.queryVoucherStock(voucherId);
        }

        String shopName = extractShopName(message);
        if (shopName != null) {
            String shops = tool.searchShopByName(shopName);
            Long shopId = extractFirstShopId(shops);
            return shopId == null ? shops : shops + "\n" + tool.queryShopVouchers(shopId);
        }

        String keyword = extractKeyword(message);
        if (isNearbyIntent(message)) {
            return tool.findNearbyShops(lat, lng, extractDistanceKm(message), keyword == null ? "美食" : keyword);
        }

        if (keyword != null) {
            return tool.findNearbyShops(lat, lng, 10.0, keyword);
        }

        if (containsAny(message, "优惠", "优惠券", "券", "折扣")) {
            return tool.findShopsWithVouchers();
        }

        return tool.findNearbyShops(lat, lng, 10.0, "美食")
                + "\n说明：当前按 " + city + " 附近美食为你推荐。";
    }

    private String formatFallbackReply(String message, String businessContext, String city, boolean realLocation) {
        String locationDesc = realLocation ? "你当前真实定位在" : "你当前手动选择的是";
        if (isNearbyIntent(message) || extractKeyword(message) != null || containsAny(message, "推荐", "好吃", "吃什么")) {
            return locationDesc + "【" + city + "】。我按这个位置帮你整理了这些结果：\n\n"
                    + businessContext
                    + "\n如果你想更准，可以点页面右上角重新定位，或者把范围改成 3 公里、5 公里。";
        }
        if (containsAny(message, "优惠", "优惠券", "券", "折扣")) {
            return "我帮你查到这些和优惠相关的信息：\n\n" + businessContext;
        }
        if (containsAny(message, "订单", "买过", "购买", "抢到", "下单")) {
            return "这是你的订单查询结果：\n\n" + businessContext;
        }
        return locationDesc + "【" + city + "】。我先按附近美食帮你整理：\n\n" + businessContext;
    }

    private String locationReply(String city, double lat, double lng, boolean realLocation) {
        if (realLocation) {
            return "是的，我现在拿到的是你的真实定位，系统判断你在【" + city + "】附近。后续“附近推荐”的距离会尽量按你当前位置计算。";
        }
        return "你现在选择的是【" + city + "】。不过这是城市中心点模式，不是你的精确位置，所以页面上的距离是“距城市中心”的估算距离。想按你本人位置推荐，可以点客服页右上角城市名重新定位。";
    }

    private boolean isLocationQuestion(String message) {
        return containsAny(message, "我在哪", "我在", "不是在", "当前城市", "当前位置", "定位", "深圳吗", "武汉吗", "西安吗");
    }

    private boolean isNearbyIntent(String message) {
        return containsAny(message, "附近", "周边", "距离", "公里", "km", "就近", "离我近", "十公里", "10公里");
    }

    private boolean containsAny(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private double extractDistanceKm(String message) {
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(公里|km|千米)").matcher(message.toLowerCase());
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        if (message.contains("十公里")) {
            return 10.0;
        }
        return 10.0;
    }

    private Long extractFirstNumberAfter(String message, String... hints) {
        if (!containsAny(message, hints)) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+)").matcher(message);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private Long extractFirstShopId(String text) {
        Matcher matcher = Pattern.compile("ID:(\\d+)").matcher(text);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    private String extractKeyword(String message) {
        String[] keywords = {
                "火锅", "烧烤", "日料", "烤肉", "海鲜", "拉面", "寿司", "牛排", "披萨",
                "甜品", "奶茶", "咖啡", "中餐", "西餐", "小吃", "KTV", "茶餐厅",
                "美发", "美甲", "足疗", "按摩", "健身", "酒吧", "亲子", "美食"
        };
        for (String keyword : keywords) {
            if (message.contains(keyword)) {
                return keyword;
            }
        }
        return null;
    }

    private String extractShopName(String message) {
        String[] knownNames = {
                "海底捞", "新白鹿", "炉鱼", "羊老三", "烤匠", "谭鸭血", "楠火锅",
                "幸福里", "星聚会", "INLOVE", "103茶餐厅", "九记"
        };
        for (String name : knownNames) {
            if (message.contains(name)) {
                return name;
            }
        }
        return null;
    }

    private String inferCity(double lat, double lng) {
        if (near(lat, lng, 22.5431, 114.0579)) return "深圳";
        if (near(lat, lng, 30.5928, 114.3055)) return "武汉";
        if (near(lat, lng, 34.3416, 108.9398)) return "西安";
        if (near(lat, lng, 30.5728, 104.0668)) return "成都";
        if (near(lat, lng, 30.2741, 120.1551)) return "杭州";
        if (near(lat, lng, 39.9042, 116.4074)) return "北京";
        if (near(lat, lng, 31.2304, 121.4737)) return "上海";
        if (near(lat, lng, 23.1291, 113.2644)) return "广州";
        return "当前位置";
    }

    private boolean near(double lat, double lng, double targetLat, double targetLng) {
        return Math.abs(lat - targetLat) < 0.8 && Math.abs(lng - targetLng) < 0.8;
    }
}
