# 11-test · 测试

> 📖 详细教程：[docs/11-test.md](../docs/11-test.md)

订单试算的完整测试示例：单元测试、切片测试、集成测试三种粒度，配 JaCoCo 覆盖率。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/orders/calculate` | 订单试算（JSON 请求体） |
| GET | `/orders/quick-calculate` | 快捷试算（查询参数） |
| GET | `/orders/hello` | 模块信息 |

## 测试结构

| 测试类 | 类型 | 用例数 | 验证什么 |
| --- | --- | --- | --- |
| `OrderServiceTest` | 单元测试（Mockito） | 16 | 满减、会员、边界、异常四类分支 |
| `OrderControllerTest` | 切片测试（`@WebMvcTest`） | 9 | 参数绑定、校验、异常映射、查询参数容错 |
| `OrderIntegrationTest` | 集成测试（`@SpringBootTest`） | 3 | 真实 Service 与 Bean 装配 |

合计 28 个用例，实跑 5.8 秒，行覆盖 94.5%、分支覆盖 88.9%。

没覆盖到的四行：`TestApplication#main` 两行（测试不调用它）、`GlobalExceptionHandler#handleOther` 两行（兜底分支，要故意造一个无人处理的异常才能触发）。

## 运行

```bash
mvn -pl 11-test test                      # 跑测试
open 11-test/target/site/jacoco/index.html # 覆盖率报告
node tools/shot-test.mjs 11-test           # 把结果渲染成截图
node tools/run-cases.mjs 11-test           # 跑接口并出图
```

## Boot 4 的包路径变化

照着 Boot 3 的教程写会编译失败：

| 类 | Boot 4 的包 |
| --- | --- |
| `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `TestRestTemplate` | `org.springframework.boot.resttestclient` |
| `@MockBean` | 已移除，用 `@MockitoBean` |
