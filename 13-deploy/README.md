# 13-deploy · 部署与 CI/CD

> 📖 详细教程：[docs/13-deploy.md](../docs/13-deploy.md)

多阶段 Dockerfile、Compose 编排、GitHub Actions 流水线，以及让应用自报构建与运行信息。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/deploy/hello` | 模块入口 |
| GET | `/deploy/info` | 构建信息（jar 内 build-info）+ 运行环境 |
| GET | `/deploy/pipeline` | 构建、打镜像、运行、编排、探活五步命令 |
| GET | `/actuator/health` | 容器探活端点 |
| GET | `/actuator/health/liveness` | 存活探针 |
| GET | `/actuator/health/readiness` | 就绪探针 |

## 关键文件

| 文件 | 作用 |
| --- | --- |
| `13-deploy/Dockerfile` | 多阶段构建：Maven 编译 → JRE 运行 |
| `13-deploy/docker-compose.yml` | 编排应用，含健康检查、资源限制、日志大小限制 |
| `.dockerignore` | 排除 `.git`、`target`、`node_modules` 等 |
| `.github/workflows/ci.yml` | 编译 → 测试 → 产物 → 镜像 → 冒烟 |

## 实测体积

| 组成 | 体积 |
| --- | --- |
| `eclipse-temurin:21-jre-alpine` 基础镜像 | 208 MB |
| jar | 22 MB |
| 最终镜像 | 230 MB（230,476,940 字节） |

对照：`maven:3.9-eclipse-temurin-21` 基础镜像单独就是 572 MB，用它做最终镜像会把编译器和源码一起带走。

`COPY` 之后再 `RUN chown` 会让 jar 在镜像里存两份，实测 252 MB；改用 `COPY --chown` 降到 230 MB。

## 测试

```bash
mvn -pl 13-deploy test
```

7 个用例，一个类 `DeployApiTest`：

| 覆盖 | 断言什么 |
| --- | --- |
| `build-info` 生效 | `build.artifact` / `version` / `time` 都有值，不是 `build.note` |
| runtime 段 | 端口是数字、pid 是数字、uptime 匹配 `N 分 N 秒` |
| 五步命令 | `pipeline` 的五个键都在 |
| 健康检查 | 整体 UP |
| 两个探针 | `liveness` 与 `readiness` 都不是 404 |
| 端点收敛 | `metrics` 返回 404（只暴露了 health 与 info） |

端口那条断言用的是格式匹配而不是具体值：`SERVER__PORT` 环境变量会覆盖 `application.yml`，`server.port=0` 时接口会报 0。

## 运行

```bash
# 本地直接跑
mvn -pl 13-deploy spring-boot:run

# 打镜像（上下文必须是仓库根，因为要读父 POM）
docker build -f 13-deploy/Dockerfile -t backend-learning/13-deploy:1.0.0 .
docker run -p 8013:8013 -e SPRING_PROFILES_ACTIVE=prod backend-learning/13-deploy:1.0.0

# 编排
docker compose -f 13-deploy/docker-compose.yml up -d --build
```

容器内验证过：`PID 1`、`started by app`（非 root）、`profiles: prod`、`os: Linux aarch64`、日志时间 `+08:00`、`docker stop` 约 226 毫秒退出、`docker ps` 显示 `(healthy)`。

内存按限额分配实测：`--memory 256m` → 堆 192 MB，`512m` → 384 MB，`1g` → 768 MB。

## 镜像里的四个安全约束

- 用 `USER app` 非 root 运行，并 `chown app:app /app`，否则非 root 用户建不了 `logs/` 目录
- 密钥走环境变量，不进镜像
- `MaxRAMPercentage` 按容器限额分配堆，不写死 `-Xmx`
- `ENTRYPOINT` 带 `exec`，让 JVM 接得到 `SIGTERM` 优雅停机

## 构建阶段的网络前提

`Dockerfile` 的 `build` 阶段要在容器内访问 Maven 仓库。受限网络下会报 `Temporary failure in name resolution`，解法是挂一份 `settings.xml` 指向国内镜像源：

```dockerfile
COPY --parents settings.xml ./
RUN mvn -B -q -s settings.xml -pl 13-deploy dependency:go-offline
```

网络不通时也可以本地 `mvn package`，只构建运行阶段——最终镜像是运行阶段的全部内容。
