# 10-file · 文件上传下载
> 📖 详细教程：[docs/10-file.md](../docs/10-file.md)

> 状态：🚧 骨架已创建，核心案例待开发

文件上传下载能力。

## 学习目标

- Multipart 上传
- 分片 / 断点续传、对接 MinIO/OSS

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| 上传 | `MultipartFile`、大小限制 |
| 分片断点 | 大文件切片、秒传 |
| 对象存储 | MinIO / OSS |
| 下载 | 流式下载、`Content-Disposition` |

## 目录结构

```
10-file
└── src/main
    ├── java/top/mqxu/file
    │   ├── FileApplication.java
    │   └── controller/FileController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 10-file -am spring-boot:run
```

端口：`8010`

## 待引入依赖

- `minio`
