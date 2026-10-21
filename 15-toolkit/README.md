# 15-toolkit · 工具库实战

> 📖 详细教程：[docs/15-toolkit.md](../docs/15-toolkit.md)

> 状态：✅ 十二个案例全部实现并实测通过

用 Hutool 把日常开发里反复要写的功能各做一个可运行接口。每个案例一个 Service 加一个 Controller，数据放在内存里，不依赖数据库和中间件，整个模块单独就能跑起来。

## 学习目标

- 用 `CaptchaUtil`、`QrCodeUtil`、`Img`、`ExcelWriter`、`ZipUtil` 产出图片、二维码、Excel、压缩包，并通过 HTTP 正确返回
- 用 `TimedCache`、`AtomicLong`、线程池撑起带状态的小功能，并清楚它们在单机场景下的适用边界
- 用 `FileTypeUtil`、`SecureUtil`、`BCrypt`、`DesensitizedUtil` 处理上传校验、接口签名、密码与敏感字段
- 分清“能跑”和“能上生产”之间差什么：哪些代码可以直接搬走，哪些必须换成 Redis、对象存储或数据库

## 十二个案例

| 序号 | 案例 | 接口 | 主要用到的类 |
| --- | --- | --- | --- |
| 1 | 图形验证码 | `GET /toolkit/captcha`<br>`POST /toolkit/captcha/verify` | `CaptchaUtil`、`MathGenerator`、`TimedCache` |
| 2 | 扫码登录 | `/toolkit/qr-login/create`、`status`、`scan`、`confirm`、`cancel`、`ascii` | `QrCodeUtil`、`QrConfig`、`JWTUtil` |
| 3 | 商品海报 | `POST /toolkit/poster` | `Img`、`ImgUtil`、`QrCodeUtil` |
| 4 | 短链服务 | `POST /toolkit/short-link`<br>`GET /toolkit/s/{code}`<br>`GET /toolkit/short-link/{code}` | `RandomUtil`、`Validator`、`TimedCache` |
| 5 | Excel 导入导出 | `/toolkit/excel/export`、`template`、`import`、`download/{fileName}` | `ExcelWriter`、`ExcelReader`、POI |
| 6 | 分片上传 | `/toolkit/chunk/init`、`upload`、`status`、`merge`、`download/{fileId}` | `FileUtil`、`DigestUtil` |
| 7 | 上传安全 | `/toolkit/file/check`、`compress`、`zip` | `FileTypeUtil`、`Img`、`ZipUtil` |
| 8 | 开放接口签名 | `POST /toolkit/sign/demo`<br>`POST /toolkit/sign/order` | `SecureUtil`、`HmacAlgorithm` |
| 9 | 敏感数据 | `/toolkit/customer`、`/{id}`、`/{id}/raw`、`/{id}/verify-password` | `AES`、`BCrypt`、`DesensitizedUtil`、`IdcardUtil` |
| 10 | 系统状态面板 | `GET /toolkit/system`<br>`GET /system.html` | `OshiUtil`、`ManagementFactory` |
| 11 | 接口巡检 | `POST /toolkit/inspect` | `ThreadUtil`、`HttpRequest` |
| 12 | 文本清洗 | `/toolkit/text/extract`、`/toolkit/text/sensitive` | `ReUtil`、`SensitiveUtil` |

## 依赖

`hutool-all` 之外有三处必须单独引，因为 Hutool 对它们是 optional 或 provided：

| 依赖 | 版本 | 原因 |
| --- | --- | --- |
| `cn.hutool:hutool-all` | 5.8.48 | 主体 |
| `com.google.zxing:core` / `javase` | 3.5.3 | 二维码，Hutool 里是 optional |
| `org.apache.poi:poi-ooxml` | 5.5.1 | Excel，Hutool 只封装 API，POI 本体要自己引 |
| `com.github.oshi:oshi-core` | 6.6.2 | 系统信息，Hutool 里是 provided |

`poi-ooxml` 要写显式版本，Spring Boot 的依赖管理里没有它。

## 目录结构

```
15-toolkit
└── src/main
    ├── java/top/mqxu/toolkit
    │   ├── ToolkitApplication.java
    │   ├── common/            Result、ErrorCode、BusinessException、GlobalExceptionHandler
    │   ├── config/            ToolkitProperties
    │   ├── controller/        12 个控制器
    │   ├── dto/               请求与响应 record
    │   ├── service/           12 个服务
    │   └── support/           QrSession、ShortLink、ChunkSession、Customer
    └── resources
        ├── application.yml
        └── static/system.html 系统状态面板
```

## 运行方式

```bash
mvn -pl 15-toolkit spring-boot:run
```

端口：`8015`，状态面板在 `http://127.0.0.1:8015/system.html`。

本机有代理时 curl 要加 `--noproxy '*'`，否则请求 127.0.0.1 也会被代理拦下来。

## 配置说明

```yaml
toolkit:
  work-dir: ${java.io.tmpdir}/toolkit-demo   # 临时文件目录，启动时自动创建
  captcha:
    expire-seconds: 60
    debug-code: true                          # 练习环境打开，响应里带答案；线上必须关
  qr-login:
    expire-seconds: 120
  short-link:
    expire-seconds: 604800
    base-url: http://127.0.0.1:8015           # 拼短链用的前缀，换端口要一起改
  sign:
    app-id: toolkit-demo
    app-secret: 3f2a9c1e5b7d4086a1c3e5f7092b4d68
    tolerance-seconds: 300
  crypto:
    aes-key: 4f8b2d6a1c9e3570                 # 必须 16、24 或 32 个字节
  chunk:
    chunk-size: 1048576
    expire-seconds: 3600
  inspect:
    timeout-millis: 3000
    max-concurrency: 8
```

## 快速验证

```bash
# 验证码（debug-code 打开时 data.code 就是答案）
curl --noproxy '*' "http://127.0.0.1:8015/toolkit/captcha?type=line"

# 生成海报，直接存成图片
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/poster -o poster.png

# 生成二维码并在终端里看
curl --noproxy '*' -X POST http://127.0.0.1:8015/toolkit/qr-login/create

# 导出订单报表
curl --noproxy '*' http://127.0.0.1:8015/toolkit/excel/export -o orders.xlsx

# 系统状态
curl --noproxy '*' http://127.0.0.1:8015/toolkit/system
```

## 生产边界

| 案例 | 本模块实现 | 上生产要换什么 |
| --- | --- | --- |
| 验证码 | `TimedCache` | Redis 共享，加 IP 限流 |
| 扫码登录 | 内存 session + 轮询 | Redis 存状态，WebSocket 推送 |
| 海报 | 同步生成 | 异步队列，产物存对象存储 |
| 短链 | `TimedCache` | Redis 或数据库，加域名白名单 |
| Excel | 内存生成 | 大数据量用 SXSSF 流式写 |
| 分片上传 | 本地磁盘 | 对象存储分片接口 |
| 上传安全 | 白名单加文件头校验 | 加病毒扫描、图片二次转码 |
| 签名 | 单调用方单密钥 | 多调用方独立密钥，支持轮换 |
| 敏感数据 | 内存 Map，密钥在 yml | 数据库存储，密钥走 KMS |
| 系统面板 | 无鉴权 | 加登录鉴权，只在内网暴露 |
| 接口巡检 | 可探测任意地址 | 地址白名单，防 SSRF |
| 文本清洗 | 固定正则与词表 | 词库外置可热更新 |
