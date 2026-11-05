# 12-actuator · 应用监控

> 📖 详细教程：[docs/12-actuator.md](../docs/12-actuator.md)

健康检查、自定义指标、Prometheus 对接。业务接口用来产生指标，监控端点负责展示。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/business/hello` | 模块入口，列出监控端点 |
| POST | `/business/orders?amount=` | 下单，计数 + 计时 |
| GET | `/business/slow?ms=` | 模拟慢请求 |
| GET | `/business/fail?reason=` | 模拟失败，带 reason 标签 |
| GET | `/actuator/health` | 健康检查（含自定义 inventory 组件） |
| GET | `/actuator/info` | 应用信息 |
| GET | `/actuator/metrics` | 指标清单 |
| GET | `/actuator/metrics/{name}` | 单个指标，支持 `tag=key:value` |
| GET | `/actuator/prometheus` | Prometheus 格式输出 |

## 指标

| 指标名（代码里） | 类型 | 含义 | Prometheus 里叫什么 |
| --- | --- | --- | --- |
| `business.orders.created` | Counter | 下单成功累计数 | `business_orders_total` |
| `business.orders.failed` | Counter | 失败累计数，按 reason 分标签 | `business_orders_failed_total` |
| `business.orders.processing` | Gauge | 当前处理中的订单数 | `business_orders_processing` |
| `business.order.handle` | Timer | 处理耗时，按 result 分标签 | `business_order_handle_seconds`（summary） |

第一行要特别注意：`created` 这个词在 Prometheus 的数据模型里是保留后缀，导出时被去掉，再被 counter 类型追加 `_total`。写 PromQL 之前先去 `/actuator/prometheus` 里 grep 一下实际名字。

## 测试

```bash
mvn -pl 12-actuator test
```

15 个用例，四个类：

| 测试类 | 用例数 | 说明 |
| --- | --- | --- |
| `BusinessMetricsTest` | 5 | 纯单元，用 `SimpleMeterRegistry` 测打点：计数、按标签分序列、Timer、Gauge |
| `InventoryHealthIndicatorTest` | 2 | 纯单元，UP 与 DOWN 两个分支 |
| `AppInfoContributorTest` | 1 | 纯单元，用 `MockEnvironment` 造配置 |
| `ActuatorEndpointTest` | 7 | 集成，从 HTTP 进来看健康检查、info、指标有没有真的变 |

`business.orders.failed` 是懒注册的：一条失败点都没打过，它不会出现在 `/actuator/metrics` 的清单里。测试里先调一次 `/business/fail` 再查清单，就是为了覆盖这个行为。

## 看 DOWN 状态

```bash
mvn -pl 12-actuator spring-boot:run \
  -Dspring-boot.run.arguments="--demo.inventory.healthy=false"

curl -i http://127.0.0.1:8012/actuator/health | head -3
# HTTP/1.1 503，body 里 status 是 DOWN，inventory 带 reason
```

## 运行

```bash
mvn -pl 12-actuator spring-boot:run
node tools/run-cases.mjs 12-actuator
```

## 要点

- 默认只暴露 `health`，其余要显式在 `management.endpoints.web.exposure.include` 里开
- 健康检查 DOWN 时返回 503，调度方靠状态码判断，不是看 body
- 标签值只能是有限集合，用 `userId` 做标签会让指标基数爆炸
- Boot 4 的 `HealthIndicator` 在 `org.springframework.boot.health.contributor` 包
- `percentiles-histogram` 那个配置只管 `http.server.requests`，业务 Timer 要在代码里调 `publishPercentileHistogram()`
- liveness 不查下游依赖，readiness 可以查
