# 13-actuator · 应用监控

> 状态：🚧 骨架已创建，核心案例待开发

可观测性。

## 学习目标

- Actuator 端点
- Micrometer 指标、Prometheus 对接

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| Actuator | `/health` `/info` `/metrics` |
| Micrometer | 自定义指标 |
| Prometheus | 指标采集与告警 |
| 健康检查 | 自定义 `HealthIndicator` |

## 目录结构

```
13-actuator
└── src/main
    ├── java/top/mqxu/actuator
    │   ├── ActuatorApplication.java
    │   └── controller/ActuatorController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 13-actuator -am spring-boot:run
```

端口：`8013`

## 待引入依赖

- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`
