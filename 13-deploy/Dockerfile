# 构建上下文是仓库根目录：
#   docker build -f 13-deploy/Dockerfile -t backend-learning/13-deploy:1.0.0 .
#
# 分成两个阶段：构建阶段带完整 JDK 与 Maven，运行阶段只要 JRE。
# 最终镜像不包含源码、Maven 缓存和构建工具，体积能小一大半。

# ---------- 第一阶段：编译打包 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# 先只复制 POM，让依赖下载单独成层。改业务代码时这一层能复用缓存。
#
# 父 POM 的 <modules> 列了全部子模块，Maven 解析时要求这些目录都存在，
# 少一个就报 "Child module ... does not exist"。所以 POM 必须全带上，
# 源码仍然只带 13-deploy 的 —— --parents 保留目录结构。
COPY --parents pom.xml ./*/pom.xml ./15-cases/*/pom.xml ./
RUN mvn -B -q -pl 13-deploy dependency:go-offline

# 再复制源码打包。after 前面的依赖层已经缓存，这一步只编译
COPY 13-deploy/src 13-deploy/src
RUN mvn -B -q -pl 13-deploy package -DskipTests

# ---------- 第二阶段：运行 ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 不用 root 跑应用：容器被突破时，攻击者拿到的也只是普通用户权限。
# 同时把 /app 的属主也给 app —— prod profile 要往 logs/ 写文件，
# 目录属主是 root 的话日志 appender 起不来，只在控制台留一行 WARN。
RUN addgroup -S app && adduser -S app -G app && chown app:app /app

# --chown 让 jar 在镜像里只存一份。写成 COPY 之后再 RUN chown，
# jar 会被复制进两个层，白白多出一个 jar 大小的体积。
COPY --chown=app:app --from=build /build/13-deploy/target/app.jar app.jar
USER app

# 时区要设，否则日志时间比本地早八小时；堆内存按容器限额的百分比分配，
# 避免 -Xmx 写死导致容器内存被超卖
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8013

# 容器自身的健康检查，编排工具会读这个状态
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -qO- http://127.0.0.1:8013/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
