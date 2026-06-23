package com.hmdp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;

    // 短信验证码防刷锁的 Key 前缀
    public static final String LOGIN_CODE_LOCK_KEY = "login:code:lock:";
    // 防刷锁的过期时间（单位：分钟）
    public static final Integer LOGIN_CODE_LOCK_TTL = 1;
    // --- 以下是针对双 Token 的新增和修改 ---

    // Access Token: 访问令牌，有效期短，拦截器校验此 Key
    public static final String LOGIN_AT_KEY = "login:at:";
    public static final Long LOGIN_AT_TTL = 30L; // 建议 30 分钟

    // Refresh Token: 刷新令牌，有效期长，仅用于换取新 AT
    public static final String LOGIN_RT_KEY = "login:rt:";
    public static final Long LOGIN_RT_TTL = 15L; // 建议 15 天
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
