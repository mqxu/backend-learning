# 10 · 文件上传下载 —— 处理文件与对象存储

> - 难度：进阶
> - 前置模块：04-web
> - 预计时长：约 45 分钟
> - 对应代码：[10-file](https://github.com/mqxu/backend-learning/tree/main/10-file)

## 1. 本节导读

学完本节，你将能：

1. 用 `MultipartFile` 接收上传的文件并保存；
2. 控制上传大小、类型校验；
3. 实现文件的流式下载；
4. 对接对象存储 MinIO。

**前置知识**：会 `04-web`（统一响应、全局异常）。

---

## 2. 概念铺垫

### 2.1 上传的本质

HTTP 上传文件，本质是把文件内容以 `multipart/form-data` 格式放在请求体里。Spring MVC 用 `MultipartFile` 把这个请求体封装成对象，方便我们操作。

### 2.2 本地存储 vs 对象存储

| 方式 | 特点 | 适用 |
| --- | --- | --- |
| 本地磁盘 | 简单，但单机、扩容难、易丢 | 小项目、临时文件 |
| 对象存储（MinIO/OSS） | 分布式、可扩展、自带访问控制 | 生产环境 |

MinIO 是开源的对象存储，兼容 S3 协议，本地即可部署，适合学习。

### 2.3 为什么要校验

上传功能是安全重灾区：不校验大小会被传爆磁盘，不校验类型可能传可执行文件。**必须在服务端校验**（前端校验可绕过）。

---

## 3. 环境准备

1. 启动 MinIO（或先用本地存储学习）；
2. 依赖：

```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
</dependency>
```

上传大小限制（`application.yml`）：

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 20MB
```

---

## 4. 动手实战

> 本模块当前是骨架，下面带你逐步补全文件能力。

### 步骤 1：本地存储上传

```java
package top.mqxu.file.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件接口
 *
 * @author mqxu
 */
@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) throws IOException {
        // 原始文件名可能带路径/特殊字符，用 UUID 重命名更安全
        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf("."));
        }
        String filename = UUID.randomUUID() + ext;

        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        file.transferTo(dir.resolve(filename));
        return filename;
    }
}
```

讲解：

- `@RequestParam("file") MultipartFile file` 接收上传；
- **规范点**：保存时用 `UUID` 重命名，避免原始文件名带来的路径穿越与重名问题；
- `transferTo` 直接落盘。

### 步骤 2：类型与大小校验

目标：只允许图片。

```java
@PostMapping("/upload/image")
public String uploadImage(@RequestParam("file") MultipartFile file) throws IOException {
    String contentType = file.getContentType();
    if (contentType == null || !contentType.startsWith("image/")) {
        throw new IllegalArgumentException("只允许上传图片");
    }
    // 再用文件头（魔数）校验更严谨，避免伪造 Content-Type
    return upload(file);
}
```

讲解：

- `getContentType()` 来自请求头，可被伪造；生产应校验文件头（魔数）。

### 步骤 3：流式下载

```java
package top.mqxu.file.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 下载接口
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/file")
public class DownloadController {

    @Value("${file.upload-dir:./uploads}")
    private String uploadDir;

    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> download(@PathVariable String filename) throws Exception {
        Path path = Paths.get(uploadDir).resolve(filename).normalize();
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
```

讲解：

- `Content-Disposition: attachment` 让浏览器以附件下载；
- `normalize()` 防止 `../` 路径穿越。

### 步骤 4：对接 MinIO

```java
package top.mqxu.file.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 配置
 *
 * @author mqxu
 */
@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(@Value("${minio.endpoint}") String endpoint,
                                   @Value("${minio.access-key}") String accessKey,
                                   @Value("${minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
```

上传改为写对象存储：

```java
minioClient.putObject(PutObjectArgs.builder()
        .bucket("files")
        .object(filename)
        .stream(file.getInputStream(), file.getSize(), -1)
        .contentType(file.getContentType())
        .build());
```

---

## 5. 完整代码与目录结构

```
10-file
└── src/main
    ├── java/top/mqxu/file
    │   ├── FileApplication.java
    │   ├── controller/FileController.java
    │   ├── controller/DownloadController.java
    │   └── config/MinioConfig.java
    └── resources/application.yml
```

---

## 6. 运行与验证

```bash
mvn -pl 10-file -am spring-boot:run
```

```bash
# 上传
curl -F "file=@/path/to/a.png" http://localhost:8010/file/upload

# 下载
curl -OJ http://localhost:8010/file/download/<返回的文件名>
```

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 上传报 413 | 文件超过 `max-file-size` | 调大限制，或前端分片 |
| 文件名乱码 | 中文名未编码 | 服务端用 UUID 重命名 |
| 路径穿越 | 文件名含 `../` | `normalize()` + UUID 重命名 |
| 大文件 OOM | 一次性读入内存 | 分片上传 / 流式写 |
| Content-Type 可伪造 | 只信请求头 | 校验文件头魔数 |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 用 curl 上传一个文本文件 | 返回文件名，本地能看到文件 |
| 2 | 上传一个 `.exe` 到图片接口 | 返回「只允许上传图片」 |
| 3 | 下载刚上传的文件 | 内容一致 |

### 8.2 课后练习（课后完成并提交）

- **基础**：给上传接口加「同名文件覆盖策略」说明，并用 UUID 保证不覆盖。
- **进阶**：实现按日期分目录存储（如 `uploads/2026/09/16/xxx.png`）。
- **挑战**：用 MinIO 实现上传 + 生成预签名下载链接，写一段话说明预签名 URL 的作用。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| `MultipartFile` | 接收上传文件 |
| 安全 | UUID 重命名 + 类型/大小/魔数校验 + 防路径穿越 |
| 下载 | `Content-Disposition` + 流式 |
| 对象存储 | MinIO，生产推荐，避免本地单机 |

下一节 [11-doc](11-doc.md) 会讲接口文档，把接口自动生成在线文档。

> 本节遵循的规范（官方 + 阿里手册）：上传文件重命名防路径穿越；服务端强制校验类型与大小；大文件流式处理；敏感文件访问加鉴权。
