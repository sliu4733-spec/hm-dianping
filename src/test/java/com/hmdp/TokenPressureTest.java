package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class TokenPressureTest {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Test
    void testCreateUsersWithDoubleToken() throws IOException {
        // 1. 准备文件路径
        PrintWriter writer = new PrintWriter(new FileWriter("D:/tokens.txt"));
        int totalTokens=100;

        for (int i = 1; i <= totalTokens; i++) {

            String phone="1800000" + String.format("%04d", i);
            // 先去数据库查一下这个手机号
            User user = userService.query().eq("phone", phone).one();

            if (user == null) {
                // 数据库没有，才创建并保存
                user = new User();
                user.setPhone(phone);
                user.setNickName("test_user_" + i);
                userService.save(user);
                // 此时 user 就是刚才保存的那个
            }
            // 3. 生成双 Token (参照你登录接口的逻辑)
            String accessToken = UUID.randomUUID().toString().replace("-", "");
            String refreshToken = UUID.randomUUID().toString().replace("-", "");

            // 4. 将用户信息转为 Map，并存入 Redis
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            // 注意：这里字段要全部转为 String，否则 Redis 取出时可能报错
            Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                    CopyOptions.create().setIgnoreNullValue(true)
                            .setFieldValueEditor((name, value) -> value.toString()));

            // 5. 存入 Redis (Key 的前缀要和你拦截器里的一致)
            String loginKey = RedisConstants.LOGIN_AT_KEY + accessToken; // 假设这是你的 AccessToken Key
            stringRedisTemplate.opsForHash().putAll(loginKey, userMap);
            // 设置有效期（压测期间建议设长一点，比如 30 分钟）
            stringRedisTemplate.expire(loginKey, 30, TimeUnit.MINUTES);

            // 6. 将 AccessToken 写入 CSV 文件给 JMeter 使用
            writer.println(accessToken);
        }
        writer.close();
        System.out.println("双 Token 压测数据准备完成！");
    }
    @Test
    void testCacheVoucher() {
        // 1. 设置你要压测的秒杀券 ID
        Long voucherId = 2L;
        // 2. 设置库存数量（比如你数据库里填的是 100）
        Integer stock = 100;

        // 3. 写入 Redis (Key 必须和你 Lua 脚本及代码里的常量一致)
        // 这里的 "seckill:stock:" 是黑马点评项目约定的前缀
        stringRedisTemplate.opsForValue().set("seckill:stock:" + voucherId, stock.toString());

        System.out.println("秒杀券库存预热成功！ID: " + voucherId + "，库存: " + stock);
    }

}
