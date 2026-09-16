# 09-security · 认证与授权

> 状态：🚧 骨架已创建，核心案例待开发

接口安全防护。

## 学习目标

- Spring Security 过滤链
- JWT 无状态认证、RBAC

## 关键知识点

| 知识点 | 说明 |
| --- | --- |
| Spring Security | 认证 / 授权、`SecurityFilterChain` |
| JWT | 签发、解析、过期 |
| RBAC | 用户 - 角色 - 权限模型 |
| 密码加密 | BCrypt |

## 目录结构

```
09-security
└── src/main
    ├── java/top/mqxu/security
    │   ├── SecurityApplication.java
    │   └── controller/SecurityController.java
    └── resources/application.yml
```

## 运行方式

```bash
mvn -pl 09-security -am spring-boot:run
```

端口：`8009`

## 待引入依赖

- `spring-boot-starter-security`
- `jjwt`
