# 14-deploy · 部署与 CI/CD

> 状态：🚧 骨架已创建，核心案例待开发

交付上线。

## 学习目标

- Dockerfile 镜像构建
- Compose 编排、CI/CD

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| Dockerfile | 多阶段构建、JRE 镜像 |
| Compose | 编排应用 + 依赖中间件 |
| 多环境 | 构建参数注入 |
| CI/CD | GitHub Actions 流水线 |

## 目录结构

```
14-deploy
└── src/main
    ├── java/top/mqxu/deploy
    │   ├── DeployApplication.java
    │   └── controller/DeployController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 14-deploy -am spring-boot:run
```

端口：`8014`

## 待引入依赖

- 无 Java 依赖 —— 主要为 Docker / CI 配置
