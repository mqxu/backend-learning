# 09-file · 文件上传下载

> 📖 详细教程：[docs/09-file.md](../docs/09-file.md)

上传、下载、预览、删除，存储层在本地磁盘和阿里云 OSS 之间用一行配置切换，业务代码不动。

## 学习目标

- `MultipartFile` 接收上传，大小、扩展名、文件头三层校验
- 按日期分目录落盘，UUID 重命名，防路径穿越
- 流式下载与浏览器内预览，中文文件名不乱码
- 存储能力抽象成接口，本地与 OSS 两套实现
- OSS 预签名 URL 与上传异常兜底

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/file/upload` | 单文件上传，参数 `file`，可选 `dir` |
| POST | `/file/upload/batch` | 多文件上传，参数 `files`，可选 `dir` |
| POST | `/file/upload/image` | 图片上传，扩展名与文件头都要求是图片 |
| GET | `/file/download/{objectKey}` | 下载，`?name=` 指定保存的文件名 |
| GET | `/file/preview/{objectKey}` | 浏览器内直接打开 |
| GET | `/file/url/{objectKey}` | 访问地址，`?expireMinutes=` 控制 OSS 签名有效期 |
| DELETE | `/file/{objectKey}` | 删除，同时清理空目录 |
| GET | `/file/storage` | 当前存储方式与限制 |

上传返回：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "objectKey": "images/2026/11/04/941a2230708a4b4384a4cd2a09b2ceaf.png",
    "originalName": "dot.png",
    "url": "/uploads/images/2026/11/04/941a2230708a4b4384a4cd2a09b2ceaf.png",
    "size": 67,
    "contentType": "image/png",
    "storage": "local"
  }
}
```

## 目录结构

```
09-file
└── src/main
    ├── java/top/mqxu/file
    │   ├── FileApplication.java
    │   ├── common/          Result、ErrorCode、BusinessException、GlobalExceptionHandler
    │   ├── config/          FileProperties、StorageType、LocalResourceConfig、AliyunOssConfig
    │   ├── controller/      FileController
    │   ├── dto/             FileUploadResult
    │   ├── service/         FileService（校验 + 生成 key）
    │   ├── storage/         StorageService、LocalStorageService、AliyunOssStorageService、StoredObject
    │   └── support/         FileTypeDetector（文件头识别）
    └── resources/application.yml

src/test/java/top/mqxu/file
├── support/FileTypeDetectorTest.java   7 个用例
└── FileApiTest.java                    14 个用例
```

## 运行方式

```bash
mvn -pl 10-file spring-boot:run
```

端口 `8010`，上传目录为 `10-file/uploads`。

```bash
# 上传
curl -F "file=@a.png" -F "dir=images" http://localhost:8010/file/upload

# 下载
curl -OJ http://localhost:8010/file/download/images/2026/11/04/xxx.png

# 浏览器直接打开
open http://localhost:8010/file/preview/images/2026/11/04/xxx.png
```

## 测试

```bash
mvn -pl 09-file test
```

21 个用例，两个类：

| 测试类 | 用例数 | 说明 |
| --- | --- | --- |
| `FileTypeDetectorTest` | 7 | 纯单元：12 种魔数、短文件、图片判断口径 |
| `FileApiTest` | 14 | 集成：上传下载删除、五类校验、批量回滚、静态映射 |

集成测试把 `file.local.base-dir` 覆盖成 `target/test-uploads`，不污染仓库里的 `uploads/`，`mvn clean` 顺手清掉。

## 切换到阿里云 OSS

`application.yml` 改两项，其余不用动：

```yaml
file:
  storage-type: oss
  oss:
    endpoint: https://oss-cn-hangzhou.aliyuncs.com
    access-key-id: ${OSS_ACCESS_KEY_ID:}
    access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
    bucket-name: ${OSS_BUCKET:}
    dir: files
    domain:            # 绑了自定义域名或 CDN 才填
```

密钥走环境变量，不写进仓库。`storage-type` 设成 `oss` 却没填密钥时，启动会直接失败并指出缺哪一项。

OSS 模式下 `url` 按 `domain` 或 bucket 默认域名拼。bucket 设为私有读时，用 `/file/url/{objectKey}` 取带签名的临时地址给前端。
