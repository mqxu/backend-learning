# 07-mq · 消息队列

> 状态：🚧 骨架已创建，核心案例待开发

异步解耦、削峰填谷。

## 学习目标

- RabbitMQ 生产 / 消费模型
- 消息可靠性、延迟队列

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| 核心模型 | Exchange / Queue / RoutingKey |
| 消息可靠性 | 确认、重试、死信队列 |
| 延迟队列 | 延时任务 |
| 削峰解耦 | 异步下单 / 通知 |

## 目录结构

```
07-mq
└── src/main
    ├── java/top/mqxu/mq
    │   ├── MqApplication.java
    │   └── controller/MqController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 07-mq -am spring-boot:run
```

端口：`8007`

## 待引入依赖

- `spring-boot-starter-amqp`
