package com.hmdp.amap;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.amap.dto.AmapAroundResponse;
import com.hmdp.amap.dto.AmapPoi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class AmapPoiClient {

    private static final int MAX_RADIUS_METERS = 50000;
    private static final int DEFAULT_OFFSET = 20;

    @Value("${amap.web-key:}")
    private String webKey;

    @Value("${amap.place-around-url:https://restapi.amap.com/v3/place/around}")
    private String placeAroundUrl;

    @Value("${amap.default-radius-meters:10000}")
    private int defaultRadiusMeters;

    @Value("${amap.cache-ttl-minutes:30}")
    private long cacheTtlMinutes;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    private final RestTemplate restTemplate = createRestTemplate();

    public List<AmapPoi> searchAround(double lat, double lng, Double radiusKm, String keyword) {
        if (webKey == null || webKey.isBlank()) {
            log.debug("Amap web key is not configured, skip external POI search.");
            return Collections.emptyList();
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword == null || normalizedKeyword.isBlank()) {
            return Collections.emptyList();
        }

        int radiusMeters = resolveRadiusMeters(radiusKm);
        String cacheKey = buildCacheKey(lat, lng, radiusMeters, normalizedKeyword);
        List<AmapPoi> cached = readCache(cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            String url = UriComponentsBuilder.fromHttpUrl(placeAroundUrl)
                    .queryParam("key", webKey)
                    .queryParam("location", lng + "," + lat)
                    .queryParam("keywords", normalizedKeyword)
                    .queryParam("radius", radiusMeters)
                    .queryParam("offset", DEFAULT_OFFSET)
                    .queryParam("page", 1)
                    .queryParam("extensions", "base")
                    .queryParam("output", "json")
                    .build()
                    .encode()
                    .toUriString();

            AmapAroundResponse response = restTemplate.getForObject(url, AmapAroundResponse.class);
            if (response == null || !response.success()) {
                log.warn("Amap POI search failed, info={}, infocode={}",
                        response == null ? null : response.getInfo(),
                        response == null ? null : response.getInfocode());
                return Collections.emptyList();
            }

            List<AmapPoi> pois = response.getPois() == null ? Collections.emptyList() : response.getPois();
            writeCache(cacheKey, pois);
            return pois;
        } catch (Exception e) {
            log.warn("Amap POI search exception, keyword={}, lat={}, lng={}", normalizedKeyword, lat, lng, e);
            return Collections.emptyList();
        }
    }

    public boolean enabled() {
        return webKey != null && !webKey.isBlank();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        switch (keyword.trim().toLowerCase()) {
            case "food":
                return "\u7f8e\u98df";
            case "ktv":
                return "KTV";
            case "hair":
                return "\u7f8e\u53d1";
            case "fitness":
                return "\u5065\u8eab\u623f";
            case "foot":
                return "\u8db3\u7597";
            case "spa":
                return "\u7f8e\u5bb9SPA";
            case "parent_child":
                return "\u4eb2\u5b50\u6e38\u4e50";
            case "bar":
                return "\u9152\u5427";
            case "party":
                return "\u8f70\u8db4\u9986";
            case "nail":
                return "\u7f8e\u7532";
            default:
                return keyword.trim();
        }
    }

    private int resolveRadiusMeters(Double radiusKm) {
        if (radiusKm == null || radiusKm <= 0) {
            return Math.min(defaultRadiusMeters, MAX_RADIUS_METERS);
        }
        return Math.min((int) Math.round(radiusKm * 1000), MAX_RADIUS_METERS);
    }

    private String buildCacheKey(double lat, double lng, int radiusMeters, String keyword) {
        double latBucket = Math.round(lat * 1000.0) / 1000.0;
        double lngBucket = Math.round(lng * 1000.0) / 1000.0;
        return "amap:poi:around:" + keyword + ":" + radiusMeters + ":" + lngBucket + ":" + latBucket;
    }

    private List<AmapPoi> readCache(String cacheKey) {
        String json = stringRedisTemplate.opsForValue().get(cacheKey);
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AmapPoi>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Read Amap POI cache failed, key={}", cacheKey, e);
            stringRedisTemplate.delete(cacheKey);
            return null;
        }
    }

    private void writeCache(String cacheKey, List<AmapPoi> pois) {
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(pois),
                    cacheTtlMinutes,
                    TimeUnit.MINUTES
            );
        } catch (JsonProcessingException e) {
            log.warn("Write Amap POI cache failed, key={}", cacheKey, e);
        }
    }

    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(3000);
        return new RestTemplate(factory);
    }
}
