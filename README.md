# hm-dianping 高并发秒杀点评系统

基于黑马点评业务场景二次优化的本地生活点评项目，包含用户登录、商铺浏览、达人探店、关注关系、签到、优惠券秒杀、AI 客服与附近推荐等功能。项目重点优化了优惠券秒杀链路，通过 Redis Lua、RabbitMQ、Redisson、MySQL 唯一索引等机制解决高并发下的超卖、重复下单和数据库瞬时压力问题。

## 项目亮点

- 使用 Redis Lua 脚本原子完成库存判断、一人一单判断和库存预扣减。
- 使用 RabbitMQ 异步削峰落库，请求线程快速返回订单 ID，消费者异步写入 MySQL。
- 使用 Redisson 分布式锁和 MySQL 唯一索引兜底，防止重复下单。
- 使用 MySQL 乐观扣减 `stock > 0` 兜底防止超卖。
- 使用 Caffeine 缓存售罄标记，减少库存售罄后的无效 Redis 请求。
- 使用 Sentinel 对秒杀接口预留 QPS 限流保护能力。
- 预留 Canal 监听 MySQL binlog 同步 Redis 的能力，减少缓存不一致窗口。
- 接入 DeepSeek 与高德 POI，提供智能客服和附近推荐扩展能力。

## 技术栈

| 类型 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 2.7.18 |
| Java | JDK 17 |
| ORM | MyBatis-Plus |
| 数据库 | MySQL |
| 缓存 | Redis、Caffeine |
| 消息队列 | RabbitMQ |
| 分布式锁 | Redisson |
| 限流保护 | Sentinel |
| binlog 同步 | Canal Client |
| 外部能力 | DeepSeek API、高德 Web 服务 API |
| 压测工具 | JMeter 5.6.3 |
| 本地依赖 | Docker、Nginx |

## 核心业务流程

### 秒杀下单流程

```text
用户请求
  -> 登录拦截器解析 token
  -> Sentinel 流控判断
  -> Caffeine 判断是否售罄
  -> Redis Lua 原子校验库存和一人一单，并预扣 Redis 库存
  -> 生成订单 ID
  -> 发送订单消息到 RabbitMQ
  -> 接口立即返回订单 ID
  -> MQ 消费者异步消费订单
  -> Redisson 用户维度加锁
  -> MySQL 查询重复订单
  -> MySQL 乐观扣减库存
  -> 保存订单
```

### Redis、RabbitMQ、MySQL 分工

```text
Redis：秒杀资格判断层，保存秒杀库存和已下单用户集合。
RabbitMQ：订单写库缓冲层，削峰并异步落库。
MySQL：最终数据存储层，通过库存乐观扣减和唯一索引兜底。
```

## 压测结果

压测接口：

```text
POST http://127.0.0.1:8081/voucher-order/seckill/5
```

测试库存：100

| 并发线程数 | 样本数 | 平均响应 | P95 | P99 | 异常率 | 吞吐量 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 500 | 500 | 22ms | 87ms | 126ms | 0.00% | 408.5/sec |
| 1000 | 1000 | 13ms | 35ms | 76ms | 0.00% | 529.9/sec |
| 1500 | 1500 | 11ms | 37ms | 75ms | 0.00% | 700.6/sec |
| 2000 | 2000 | 20ms | 86ms | 132ms | 0.00% | 842.5/sec |
| 2500 | 2500 | 12ms | 29ms | 65ms | 0.00% | 893.5/sec |

2500 并发压测后校验结果：

```text
订单数 = 100
MySQL 库存 = 0
Redis 库存 = 0
Redis 下单用户集合 = 100
重复下单查询 = 0 rows
RabbitMQ 主队列无积压
RabbitMQ 死信队列为 0
```

3000 线程压测时出现连接拒绝，异常率约 44.27%，说明当前本机环境下 3000 并发接近或超过服务承载边界。因此简历和项目说明中采用 2500 并发作为稳定压测结果。

## 主要功能

- 手机号验证码登录，Redis 保存验证码和登录态。
- 商铺分类、商铺详情、附近商铺查询。
- Redis 缓存商铺详情，支持缓存穿透保护。
- Redis GEO 支持附近商铺查询。
- 用户签到，使用 Redis Bitmap 统计连续签到。
- 探店博客发布、点赞、点赞排行榜。
- 关注、取关、共同关注，使用 Redis Set 求交集。
- 优惠券秒杀，一人一单，库存防超卖。
- RabbitMQ 异步订单落库。
- AI 客服与附近推荐扩展。

## 本地运行

### 1. 环境要求

```text
JDK 17
Maven
MySQL
Redis
RabbitMQ
```

### 2. 创建数据库

创建数据库：

```sql
CREATE DATABASE hmdp DEFAULT CHARACTER SET utf8mb4;
```

导入 SQL：

```text
src/main/resources/db/hmdp.sql
```

### 3. 启动 Redis

```powershell
docker run -d --name my-redis -p 6379:6379 redis:7.2.4 redis-server --requirepass 1234
```

已有容器时：

```powershell
docker start my-redis
```

### 4. 启动 RabbitMQ

```powershell
docker run -d --name mq `
  -p 5672:5672 `
  -p 18080:15672 `
  -e RABBITMQ_DEFAULT_USER=admin `
  -e RABBITMQ_DEFAULT_PASS=123456 `
  rabbitmq:3-management
```

已有容器时：

```powershell
docker start mq
```

RabbitMQ 管理台：

```text
http://localhost:18080
admin / 123456
```

### 5. 启动后端

使用 IDEA 选择 JDK 17，运行：

```text
com.hmdp.HmDianPingApplication
```

测试接口：

```text
http://127.0.0.1:8081/shop-type/list
```

## 配置说明

默认配置位于：

```text
src/main/resources/application.yaml
```

支持通过环境变量覆盖：

```text
MYSQL_URL
MYSQL_USERNAME
MYSQL_PASSWORD
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
RABBITMQ_HOST
RABBITMQ_PORT
RABBITMQ_USERNAME
RABBITMQ_PASSWORD
DEEPSEEK_API_KEY
AMAP_WEB_KEY
```

Canal 当前默认关闭：

```yaml
canal:
  enabled: false
```

如果需要启用 Canal，需要额外部署 Canal Server，并开启 MySQL binlog。

## 文档

更详细的项目说明和代码实现分析见：

```text
docs/项目说明书.md
docs/技术实现说明.md
```

## 简历描述参考

针对优惠券秒杀场景，基于 Redis Lua 脚本原子完成库存校验、一人一单校验与库存预扣减，通过 RabbitMQ 异步削峰落库，结合 Redisson 分布式锁与 MySQL 唯一索引兜底防止重复下单，并采用 Redis 预扣库存 + MySQL 乐观扣减防止超卖。JMeter 压测 2500 并发请求下，接口异常率 0%，吞吐量约 893 TPS，平均响应 12ms，P95 29ms，P99 65ms；最终订单数与库存一致，无超卖、无重复下单，RabbitMQ 无积压和死信消息。
