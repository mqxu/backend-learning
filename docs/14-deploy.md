# 14 · 部署与 CI/CD —— 把应用交付上线

> - 难度：进阶
> - 前置模块：13-actuator
> - 预计时长：约 45 分钟
> - 对应代码：[14-deploy](../14-deploy)

## 1. 本节导读

学完本节，你将能：

1. 写多阶段 `Dockerfile` 构建轻量镜像；
2. 用 `docker-compose.yml` 编排应用与依赖中间件；
3. 用 GitHub Actions 搭一条自动化流水线。

**前置知识**：会一个可打包运行的模块（如 `05-mybatis`）。

---

## 2. 概念铺垫

### 2.1 为什么用容器

「在我机器上能跑」是部署的头号难题——环境不一致。Docker 把应用和它的运行环境打包成**镜像**，镜像在任何装了 Docker 的机器上行为一致，彻底解决环境问题。

### 2.2 多阶段构建

镜像里如果不做清理，会把 JDK、构建工具、源码全打包进去，又大又危险。**多阶段构建**：第一阶段用完整环境编译，第二阶段只把编译产物（jar）拷进精简的 JRE 镜像，最终镜像又小又干净。

### 2.3 CI/CD 是什么

- **CI（持续集成）**：每次提交自动编译、测试；
- **CD（持续交付/部署）**：通过测试后自动部署。

GitHub Actions 是 GitHub 提供的免费流水线，用 YAML 定义「提交后做什么」。

---

## 3. 环境准备

- 安装 Docker、Docker Compose；
- 有一个能打包的 Spring Boot 模块。

---

## 4. 动手实战

> 本模块当前是骨架，下面带你逐步补全部署配置。

### 步骤 1：多阶段 Dockerfile

```dockerfile
# 第一阶段：构建
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY . .
RUN mvn -pl 05-mybatis -am clean package -DskipTests

# 第二阶段：运行（只用 JRE）
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/05-mybatis/target/*.jar app.jar
EXPOSE 8005
ENTRYPOINT ["java", "-jar", "app.jar"]
```

讲解：

- `AS builder` 命名第一阶段；`COPY --from=builder` 只取产物 jar；
- 运行阶段用 `jre` 镜像，体积远小于 JDK；
- `ENTRYPOINT` 指定启动命令。

### 步骤 2：docker-compose 编排

目标：应用 + MySQL 一键起。

```yaml
version: "3.8"

services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: backend_learning
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql

  app:
    build: .
    depends_on:
      - mysql
    ports:
      - "8005:8005"
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/backend_learning
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: root

volumes:
  mysql-data:
```

讲解：

- 应用通过服务名 `mysql` 访问数据库（Compose 内部网络自动解析）；
- 数据库连接等敏感信息通过环境变量注入，对应 `02-config` 的「外部化配置」。

### 步骤 3：GitHub Actions 流水线

`.github/workflows/ci.yml`：

```yaml
name: CI

on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 21
      - name: Build and test
        run: mvn clean package
```

讲解：

- `on.push` 表示推到 main 分支就触发；
- `checkout` 拉代码、`setup-java` 配 JDK、`mvn clean package` 编译并测试；
- 任何一步失败，流水线变红，提交者第一时间知道。

---

## 5. 完整目录结构

```
14-deploy
├── Dockerfile
├── docker-compose.yml
└── .github/workflows/ci.yml
```

---

## 6. 运行与验证

```bash
# 构建并运行
docker compose up -d

# 查看日志
docker compose logs -f app

# 停止
docker compose down
```

流水线验证：推送到 GitHub 后，到仓库 Actions 标签页查看构建是否绿色通过。

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 镜像巨大 | 没做多阶段构建 | 拆出 builder 阶段，运行用 JRE |
| 应用连不上 MySQL | 用了 `localhost` | Compose 内用服务名 `mysql` |
| 每次全量构建慢 | 没利用镜像层缓存 | 先 `COPY pom.xml` 下载依赖，再 `COPY` 源码 |
| 流水线失败 | 本地能过、CI 环境缺依赖 | 看 CI 日志，环境要一致（JDK 版本等） |
| 明文密码进镜像 | 把密钥写进镜像 | 用环境变量/密钥管理注入 |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 构建 `05-mybatis` 的 Docker 镜像 | `docker images` 看到镜像 |
| 2 | `docker compose up -d` 起 MySQL | 容器运行正常 |
| 3 | 对比多阶段与单阶段镜像大小 | 多阶段明显更小 |

### 8.2 课后练习（课后完成并提交）

- **基础**：给 `05-mybatis` 写一个可运行的 `Dockerfile` + `docker-compose.yml`。
- **进阶**：优化 Dockerfile，利用镜像层缓存加速重复构建（先 `COPY pom.xml`）。
- **挑战**：扩展 GitHub Actions，增加「构建 Docker 镜像并推送到 Docker Hub」的步骤，写出完整 YAML。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| Docker | 镜像打包环境，行为一致 |
| 多阶段构建 | 编译、运行分离，镜像更小更安全 |
| Compose | 应用 + 依赖一键编排 |
| CI/CD | 提交即触发构建/测试/部署 |

至此 14 个模块学完，从「快速入门」到「部署上线」的完整后端工程链路已打通。

> 本节遵循的规范（官方 + 阿里手册）：镜像用多阶段构建 + JRE；密钥走环境变量/密钥管理，不进镜像；流水线保证环境一致。
