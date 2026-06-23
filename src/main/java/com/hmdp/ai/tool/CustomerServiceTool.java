package com.hmdp.ai.tool;

import com.hmdp.amap.AmapPoiClient;
import com.hmdp.amap.dto.AmapPoi;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IShopService;
import com.hmdp.service.IShopTypeService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CustomerServiceTool {

    private static final int DEFAULT_AI_LIMIT = 8;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private IShopService shopService;

    @Resource
    private IVoucherService voucherService;

    @Resource
    private IShopTypeService shopTypeService;

    @Resource
    private AmapPoiClient amapPoiClient;

    public String findShopsWithVouchers() {
        List<Voucher> vouchers = voucherService.list();
        if (vouchers == null || vouchers.isEmpty()) {
            return "目前没有可用优惠券。";
        }

        Map<Long, Long> shopVoucherCount = vouchers.stream()
                .collect(Collectors.groupingBy(Voucher::getShopId, Collectors.counting()));

        StringBuilder result = new StringBuilder("当前有优惠券的店铺：\n");
        int index = 0;
        for (Map.Entry<Long, Long> entry : shopVoucherCount.entrySet()) {
            Shop shop = shopService.getById(entry.getKey());
            if (shop == null) {
                continue;
            }
            result.append(++index)
                    .append(". ID:").append(shop.getId())
                    .append(" | ").append(shop.getName())
                    .append(" | 评分:").append(formatScore(shop))
                    .append(" | 优惠券:").append(entry.getValue()).append("张\n");
            if (index >= DEFAULT_AI_LIMIT) {
                break;
            }
        }
        return index == 0 ? "目前没有可用优惠券。" : result.toString();
    }

    public String queryShopVouchers(Long shopId) {
        Shop shop = shopService.getById(shopId);
        String shopName = shop == null ? "店铺" + shopId : shop.getName();

        List<Voucher> vouchers = voucherService.query().eq("shop_id", shopId).list();
        if (vouchers == null || vouchers.isEmpty()) {
            return "【" + shopName + "】暂无优惠券。";
        }

        Set<Long> voucherIds = vouchers.stream().map(Voucher::getId).collect(Collectors.toSet());
        Map<Long, SeckillVoucher> seckillVoucherMap = new HashMap<>();
        if (!voucherIds.isEmpty()) {
            List<SeckillVoucher> seckillVouchers = seckillVoucherService.listByIds(voucherIds);
            if (seckillVouchers != null) {
                for (SeckillVoucher seckillVoucher : seckillVouchers) {
                    seckillVoucherMap.put(seckillVoucher.getVoucherId(), seckillVoucher);
                }
            }
        }

        StringBuilder result = new StringBuilder("【").append(shopName).append("】优惠券：\n");
        int index = 0;
        for (Voucher voucher : vouchers) {
            SeckillVoucher seckillVoucher = seckillVoucherMap.get(voucher.getId());
            result.append(++index)
                    .append(". ID:").append(voucher.getId())
                    .append(" | ").append(voucher.getTitle())
                    .append(" | 支付").append(formatMoney(voucher.getPayValue()))
                    .append(" | 抵扣").append(formatMoney(voucher.getActualValue()));
            if (seckillVoucher != null) {
                result.append(" | 秒杀库存:").append(seckillVoucher.getStock() == null ? 0 : seckillVoucher.getStock());
            }
            result.append("\n");
            if (voucher.getRules() != null && !voucher.getRules().isBlank()) {
                result.append("   规则:").append(voucher.getRules()).append("\n");
            }
        }
        return result.toString();
    }

    public String queryVoucherStock(Long voucherId) {
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return "没有找到该秒杀优惠券。";
        }
        Voucher voucher = voucherService.getById(voucherId);
        String title = voucher == null ? "优惠券" + voucherId : voucher.getTitle();
        int stock = seckillVoucher.getStock() == null ? 0 : seckillVoucher.getStock();
        return "【" + title + "】当前秒杀库存：" + stock + " 张。";
    }

    public String recommendShopsByKeyword(String keyword) {
        List<Shop> shops = queryByKeyword(keyword);
        if (shops == null || shops.isEmpty()) {
            return "没有找到和【" + keyword + "】相关的店铺。可以试试火锅、烧烤、日料、KTV等关键词。";
        }
        shops.sort(scoreComparator());
        return formatShopList("为你找到和【" + keyword + "】相关的店铺：", shops.subList(0, Math.min(DEFAULT_AI_LIMIT, shops.size())));
    }

    public String recommendShopsByRating(String keyword, int limit) {
        List<Shop> shops = keyword == null || keyword.isBlank()
                ? shopService.list()
                : queryByKeyword(keyword);
        if (shops == null || shops.isEmpty()) {
            return keyword == null ? "暂无店铺数据。" : "没有找到和【" + keyword + "】相关的店铺。";
        }

        shops.sort(scoreComparator());
        return formatShopList("评分较高的店铺：", shops.subList(0, Math.min(limit, shops.size())));
    }

    public String listShopTypes() {
        List<ShopType> types = shopTypeService.list();
        if (types == null || types.isEmpty()) {
            return "暂无店铺分类。";
        }
        StringBuilder result = new StringBuilder("当前店铺分类：\n");
        for (ShopType type : types) {
            result.append("- ").append(type.getName()).append("\n");
        }
        return result.toString();
    }

    public String findNearbyShops(Double lat, Double lng, Double radiusKm, String keyword) {
        double userLat = lat == null ? 30.2741 : lat;
        double userLng = lng == null ? 120.1551 : lng;
        double radius = radiusKm == null || radiusKm <= 0 ? 10.0 : radiusKm;

        List<Shop> shops = keyword == null || keyword.isBlank()
                ? shopService.list()
                : queryByKeyword(keyword);
        if (shops == null || shops.isEmpty()) {
            return keyword == null
                    ? "没有找到店铺数据。"
                    : "没有找到和【" + keyword + "】相关的店铺数据。";
        }

        List<Map.Entry<Shop, Double>> nearby = new ArrayList<>();
        for (Shop shop : shops) {
            if (shop.getX() == null || shop.getY() == null) {
                continue;
            }
            double distance = haversine(userLat, userLng, shop.getY(), shop.getX());
            if (distance <= radius) {
                nearby.add(new AbstractMap.SimpleEntry<>(shop, distance));
            }
        }
        nearby.sort(Comparator.comparingDouble(Map.Entry::getValue));

        List<AmapPoi> amapPois = amapPoiClient.searchAround(userLat, userLng, radius, keyword);
        if (nearby.isEmpty() && amapPois.isEmpty()) {
            String flavor = keyword == null || keyword.isBlank() ? "店铺" : "【" + keyword + "】店铺";
            return "附近 " + formatRadius(radius) + " 公里内暂时没有找到" + flavor + "。可以扩大到 15 公里，或换个口味关键词。";
        }

        String flavor = keyword == null || keyword.isBlank() ? "店铺" : "【" + keyword + "】店铺";
        StringBuilder result = new StringBuilder("附近 ").append(formatRadius(radius)).append(" 公里内的").append(flavor).append("：\n");
        int index = 0;
        for (Map.Entry<Shop, Double> entry : nearby) {
            Shop shop = entry.getKey();
            result.append(++index)
                    .append(". ID:").append(shop.getId())
                    .append(" | ").append(shop.getName())
                    .append(" | 距离:").append(String.format("%.1f", entry.getValue())).append("km")
                    .append(" | 评分:").append(formatScore(shop))
                    .append(" | 人均:").append(shop.getAvgPrice() == null ? "暂无" : "￥" + shop.getAvgPrice())
                    .append("\n   地址:").append(defaultText(shop.getAddress()))
                    .append("\n");
            if (index >= DEFAULT_AI_LIMIT) {
                break;
            }
        }
        int externalIndex = 0;
        for (AmapPoi poi : amapPois) {
            if (index >= DEFAULT_AI_LIMIT) {
                break;
            }
            result.append(++index)
                    .append(". ").append(defaultText(poi.getName()))
                    .append(" | 距离:").append(formatAmapDistance(poi.getDistance()))
                    .append(" | 来源:高德POI")
                    .append("\n   地址:").append(defaultText(poi.getAddress()))
                    .append("\n");
            externalIndex++;
        }
        if (externalIndex > 0) {
            result.append("提示：带“来源:高德POI”的店铺来自高德周边搜索，可用于真实附近推荐展示。\n");
        }
        return result.toString();
    }

    public String searchShopByName(String keyword) {
        List<Shop> shops = shopService.query()
                .like("name", keyword)
                .last("LIMIT 10")
                .list();
        if (shops == null || shops.isEmpty()) {
            return "未找到名称包含【" + keyword + "】的店铺。";
        }
        return formatShopList("店铺搜索结果：", shops);
    }

    public String queryShopDetail(Long shopId) {
        Result result = shopService.queryById(shopId);
        if (result == null || !result.getSuccess()) {
            return "未找到 ID 为 " + shopId + " 的店铺。";
        }
        Shop shop = (Shop) result.getData();
        return "【" + shop.getName() + "】\n"
                + "地址：" + defaultText(shop.getAddress()) + "\n"
                + "商圈：" + defaultText(shop.getArea()) + "\n"
                + "评分：" + formatScore(shop) + "\n"
                + "人均：" + (shop.getAvgPrice() == null ? "暂无" : "￥" + shop.getAvgPrice()) + "\n"
                + "营业时间：" + defaultText(shop.getOpenHours()) + "\n"
                + "销量：" + (shop.getSold() == null ? 0 : shop.getSold());
    }

    public String queryUserOrders(Long userId) {
        List<VoucherOrder> orders = voucherOrderService.query()
                .eq("user_id", userId)
                .orderByDesc("create_time")
                .last("LIMIT 20")
                .list();
        if (orders == null || orders.isEmpty()) {
            return "你还没有订单。";
        }

        StringBuilder result = new StringBuilder("最近 ").append(orders.size()).append(" 笔订单：\n");
        for (VoucherOrder order : orders) {
            result.append("- 订单ID:").append(order.getId())
                    .append(" | 优惠券ID:").append(order.getVoucherId())
                    .append(" | 状态:").append(formatOrderStatus(order.getStatus()))
                    .append(" | 下单时间:").append(order.getCreateTime() == null ? "暂无" : order.getCreateTime())
                    .append("\n");
        }
        return result.toString();
    }

    public static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private List<Shop> queryByKeyword(String keyword) {
        return shopService.query()
                .and(wrapper -> wrapper.like("name", keyword).or().like("address", keyword).or().like("area", keyword))
                .list();
    }

    private Comparator<Shop> scoreComparator() {
        return (left, right) -> Integer.compare(
                right.getScore() == null ? 0 : right.getScore(),
                left.getScore() == null ? 0 : left.getScore()
        );
    }

    private String formatShopList(String title, List<Shop> shops) {
        StringBuilder result = new StringBuilder(title).append("\n");
        int index = 0;
        for (Shop shop : shops) {
            result.append(++index)
                    .append(". ID:").append(shop.getId())
                    .append(" | ").append(shop.getName())
                    .append(" | 评分:").append(formatScore(shop))
                    .append(" | 人均:").append(shop.getAvgPrice() == null ? "暂无" : "￥" + shop.getAvgPrice())
                    .append("\n   地址:").append(defaultText(shop.getAddress()))
                    .append("\n");
        }
        return result.toString();
    }

    private String formatScore(Shop shop) {
        return shop.getScore() == null ? "暂无" : String.format("%.1f", shop.getScore() / 10.0);
    }

    private String formatMoney(Long cents) {
        return cents == null ? "暂无" : "￥" + String.format("%.2f", cents / 100.0);
    }

    private String defaultText(String text) {
        return text == null || text.isBlank() ? "暂无" : text;
    }

    private String formatRadius(double radius) {
        return radius == Math.floor(radius) ? String.format("%.0f", radius) : String.format("%.1f", radius);
    }

    private String formatAmapDistance(String distanceMeters) {
        if (distanceMeters == null || distanceMeters.isBlank()) {
            return "未知";
        }
        try {
            double meters = Double.parseDouble(distanceMeters);
            return meters < 1000 ? String.format("%.0fm", meters) : String.format("%.1fkm", meters / 1000.0);
        } catch (Exception e) {
            return distanceMeters + "m";
        }
    }

    private String formatOrderStatus(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case 1:
                return "未支付";
            case 2:
                return "已支付";
            case 3:
                return "已核销";
            case 4:
                return "已取消";
            case 5:
                return "退款中";
            case 6:
                return "已退款";
            default:
                return "未知";
        }
    }
}
