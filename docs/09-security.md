# 09 · 认证与授权 —— 用 Spring Security 保护接口

> - 难度：进阶
> - 前置模块：04-web（统一响应、全局异常）、05-mybatis（用户存库）
> - 课时：90 分钟（含随堂练习），课后作业另计
> - 对应代码：`09-security`，14 个接口测试用例
> - 版本基线：Spring Boot 4.1.1、Spring Security 7.1.1、jjwt 0.13.0、Java 21

---

## 0. 这一节在课程里的位置

前面几个模块做出来的接口是"谁都能调"的。任何人拿到地址就能删数据，这在上线评审里是一票否决项。本节解决的是同一件事的两面：让服务端确认调用者是谁，并决定这个人能做什么。

学完本节，模块里会长出一个完整的安全骨架：登录签发令牌、每个请求验令牌、按角色和权限点放行、失败时给出正确的 401 与 403。后面 10 到 14 模块的接口都会套用这套骨架。

---

## 1. 学完能做什么

四条可以当场验证的能力，不是"了解"而是"能改"：

1. 能改安全规则：把某个接口从"需要登录"改成"公开"，或者反过来，改完立刻能验证效果。
2. 能加一个角色：给角色补权限点，让普通用户能读某类数据但仍不能写。
3. 能解释 401 和 403 的差别，并说清这两种响应分别由代码里哪个类产生。
4. 能徒手解码一个 JWT，指出 `exp`、`sub`、`iss` 各是什么，并判断这个令牌是否已过期。

第 3 条是分水岭。多数人只会看状态码，说不出是谁写的响应，遇到"我配了全局异常处理器却没有返回 JSON"就卡住。

---

## 2. 课时安排

| 时间 | 环节 | 形式 | 对应章节 |
| --- | --- | --- | --- |
| 0-5 分钟 | 开场：一个没有鉴权的接口被删库 | 演示 + 提问 | 第 0、1 节 |
| 5-25 分钟 | 概念铺垫：认证、授权、JWT、过滤链 | 讲授 + 板书 | 第 3 节 |
| 25-30 分钟 | 环境准备，跑起骨架 | 动手 | 第 4 节 |
| 30-50 分钟 | 步骤 1 到 5：账号、密码、登录校验 | 教师演示 + 学生跟做 | 第 5 节前半 |
| 50-60 分钟 | 休息 | | |
| 60-80 分钟 | 步骤 6 到 10：JWT、过滤器、过滤链、鉴权 | 教师演示 + 学生跟做 | 第 5 节后半 |
| 80-90 分钟 | 随堂练习 4 题 + 讲评 | 学生动手 | 第 10 节 |

教师演示时建议顺序反过来讲一次：先跑最终代码看效果（三个账号分别登录、访问、被拒），再回头拆解怎么实现。学生先看到结果，后面每一步都知道自己在拼什么。

---

## 3. 概念铺垫

### 3.1 认证与授权

| 概念 | 回答的问题 | 本模块的落点 | 失败时 |
| --- | --- | --- | --- |
| 认证 Authentication | 你是谁 | `/auth/login` 校验密码后签发令牌 | 401 |
| 授权 Authorization | 你能做什么 | URL 规则、`@PreAuthorize` | 403 |

用小区打比方：门禁刷卡进小区是认证，进单元楼和地下车库要不要再验是授权。刷卡失败进不去小区，这是 401；进了小区但刷不开机房，这是 403。

这两件事在教学里必须分开讲，因为它们在代码里由不同的类负责，失败时写响应的也是不同的类。混在一起讲，学生遇到 403 会去查登录逻辑，方向就错了。

### 3.2 会话放在服务端还是客户端

| 对比项 | 服务端 session | 无状态令牌（JWT） |
| --- | --- | --- |
| 登录状态存哪 | 服务端内存或 Redis | 客户端自己保存，服务端不存 |
| 多台服务器 | 要共享 session 或做粘性会话 | 每台机器拿同一密钥就能验 |
| 主动注销 | 删 session 即刻生效 | 令牌签发后到期前一直有效，需黑名单或版本号 |
| 改密码后旧令牌 | 删 session 即刻失效 | 同上，需额外机制 |
| 每次请求开销 | 一次 session 查询 | 一次验签，加一次用户回查 |
| 前端跨域 | 依赖 cookie，配置麻烦 | 走请求头，配置简单 |

表中"主动注销"那一行是本模块的已知短板：令牌有效期两小时，用户点了退出，令牌在到期前仍然能用。要做到立刻失效，得引入黑名单或令牌版本号。这是选型代价，不是 bug，要跟学生讲清。第 11 节的挑战题就落在这里。

### 3.3 JWT 逐段拆开看

下图是登录接口签发的一个令牌，为了排版分三行显示，真实值是一行。这个示例的 `exp` 已经过期，只用来观察结构，不要拿去调接口：

```
eyJhbGciOiJIUzI1NiJ9
.eyJpc3MiOiJiYWNrZW5kLWxlYXJuaW5nIiwic3ViIjoiYWRtaW4iLCJpYXQiOjE3OTMxMTg0NDIsImV4cCI6MTc5MzEyNTY0Miwicm9sZXMiOlsiQURNSU4iXX0
.b6buk39BUiruQip-qdpABaz2MDUwfKTBADv3NPwngJw
```

把前两段做 Base64URL 解码，得到：

| 段 | 解码后内容 | 作用 | 是否加密 |
| --- | --- | --- | --- |
| 第一段 Header | `{"alg":"HS256"}` | 声明签名算法 | 否，明文 |
| 第二段 Payload | `{"iss":"backend-learning","sub":"admin","iat":1793118442,"exp":1793125642,"roles":["ADMIN"]}` | 身份与元信息 | 否，明文 |
| 第三段 Signature | 二进制签名 | 防篡改 | 不可逆 |

签名的算法是：把前两段用点连起来，用密钥对这段字符串做 HMAC-SHA256，结果再做 Base64URL。因此改任何一段的一个字符，签名就对不上，验签失败。

两个教学要点：

- 载荷是明文，只是 Base64 编码，不是加密。任何人拿到令牌都能读到里面的内容。所以密码、身份证号、手机号不能放进去，本模块只放用户名和用于展示的角色名。
- 令牌自带过期时间 `exp`，服务端解析时会校验，不需要自己写过期判断。同时要校验签发方 `iss`，否则别的系统用同密钥签的令牌也能通过。

学生常问：既然能读到角色，为什么权限还要回查数据库？因为令牌一旦签发就无法修改，管理员把某人降级后，旧令牌里还写着 ADMIN。本模块的做法是令牌只承载身份，权限每次请求按用户名回查，改角色立刻生效，代价是每个请求多一次查询。

### 3.4 一次请求经过哪些过滤器

```
客户端
  │  Authorization: Bearer <token>
  ▼
Tomcat
  ▼
Spring Security 过滤器链
  ├─ CsrfFilter（本模块已关闭）
  ├─ JwtAuthenticationFilter      ← 我们自己插入的位置
  │     验签 → 按用户名回查账号 → 写入 SecurityContext
  ├─ AnonymousAuthenticationFilter（没认证时放一个匿名身份）
  ├─ ExceptionTranslationFilter  ← 把认证/授权异常翻译成响应
  │     未认证 → RestAuthenticationEntryPoint → 401
  │     权限不足 → RestAccessDeniedHandler → 403
  └─ AuthorizationFilter          ← 判断这条 URL 要不要放行
  ▼
DispatcherServlet
  ▼
Controller（@PreAuthorize 在这里再判一次）
```

我们要插的位置必须在 `AuthorizationFilter` 之前，否则授权判断时 `SecurityContext` 还是空的，全部请求都会被当成匿名。代码里写成：

```java
.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
```

`UsernamePasswordAuthenticationFilter` 在链中的位置本来就早于授权判断，用它当锚点既符合语义，也不受 Security 内部顺序调整影响。

### 3.5 401 与 403 各自在哪里产生

| 场景 | 抛出位置 | 写响应的地方 | 状态码 |
| --- | --- | --- | --- |
| 没带令牌访问需要登录的接口 | `AuthorizationFilter` | `RestAuthenticationEntryPoint` | 401 |
| 令牌过期、被篡改、用户已被删 | 同上 | 同上 | 401 |
| 已登录但角色不够（URL 规则拦截） | 同上 | `RestAccessDeniedHandler` | 403 |
| 已登录但方法级注解拒绝 | Controller 方法调用中 | `GlobalExceptionHandler` | 403 |

这张表解释了本节最容易卡住的现象：只配了 `@RestControllerAdvice`，401 和 403 却没有 JSON 响应。原因是前三种情况发生在进 Controller 之前，异常根本没到 Spring MVC，全局异常处理器接不到。必须在过滤器层自己写响应体。

第 4 行又不一样：`@PreAuthorize` 是在 Controller 方法被调用时抛异常，属于 MVC 处理范围内，所以归全局异常处理器管。两条路径都要有，缺一条就会出现"有的 403 有 JSON、有的没有"。

### 3.6 RBAC：用户、角色、权限点

```
用户 admin ──┬── 角色 ADMIN ──┬── 权限点 user:read
             │                ├── 权限点 user:write
             │                └── 权限点 report:read
             │
用户 teacher ──── 角色 USER  ──┬── 权限点 report:read
```

接口绑权限点而不是绑角色，好处是加一类人不用改接口代码：将来出现"审计员"角色，只要给它 `report:read`，报表接口不用动。

本模块把两者写在一个枚举里：

| 角色 | 换到的权限 |
| --- | --- |
| ADMIN | `ROLE_ADMIN`、`user:read`、`user:write`、`report:read` |
| USER | `ROLE_USER`、`report:read` |

`ROLE_` 前缀是 Spring Security 的历史约定：`hasRole("ADMIN")` 内部会拼成 `ROLE_ADMIN` 去比对，配置里写了 `ROLE_ADMIN` 反而对不上。而 `hasAuthority` 是原样比对。这两个方法混用是初学者最常见的错。

### 3.7 为什么用 Spring Security，不自己写拦截器

| 能力 | 自己写 HandlerInterceptor | Spring Security |
| --- | --- | --- |
| 密码加密 | 自己处理 | `PasswordEncoder` + BCrypt |
| 账号密码校验流程 | 自己写比对与异常分支 | `AuthenticationManager`，禁用/锁定账号自动拦 |
| 方法级鉴权 | 自己写注解与切面 | `@PreAuthorize` 一行 |
| 认证/授权异常翻译 | 自己判断状态码 | 过滤器链自动分流到 401/403 |
| CSRF、会话固定、并发会话 | 基本不会做 | 开箱支持 |
| 测试支持 | 自己造上下文 | `@WithMockUser`、MockMvc 集成 |

自己写拦截器在只有一个角色的小项目里能跑，一旦出现"部分接口按权限点、部分按数据归属"就会失控。本节按官方组件式写法来，学生以后换项目也能对上。

---

## 4. 环境准备

### 4.1 依赖

`09-security/pom.xml`：

```xml
<properties>
    <jjwt.version>0.13.0</jjwt.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>${jjwt.version}</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

| 依赖 | 版本来源 | 说明 |
| --- | --- | --- |
| `spring-boot-starter-security` | 父 POM 管理，实为 Security 7.1.1 | 过滤链、BCrypt、方法级鉴权 |
| `spring-boot-starter-validation` | 父 POM 管理 | 登录与新增用户的入参校验 |
| `jjwt-api` | 手写 0.13.0 | 编译期要用的接口 |
| `jjwt-impl` | 手写 0.13.0，runtime | 运行时实现 |
| `jjwt-jackson` | 手写 0.13.0，runtime | 令牌载荷的 JSON 读写 |

三个 jjwt 构件的关系经常被问：`api` 只有接口和少量实现，`impl` 才是真正的算法实现，`jackson` 负责把载荷 Map 转成 JSON。后两个只在运行时用，所以写 `runtime`，不让业务代码直接依赖实现类。

jjwt 不在 Spring Boot 的依赖管理清单里，版本必须自己写；漏写版本会直接编译失败。另外 `jjwt-jackson` 依赖的是 Jackson 2，而 Spring Boot 4 的 HTTP 序列化走 Jackson 3，两套 JSON 库同时存在于 classpath 上，各管一段，不冲突。

### 4.2 引入 starter 后的默认行为

只加依赖、不写任何配置时，启动日志会出现一行随机密码：

```
Using generated security password: 3f1a2c9e-...
```

访问任意接口会被重定向到一个自动生成的登录页。这是 starter 的默认保护策略，适合快速演示，不适合本项目。我们要做三件事把它接管过来：

1. 提供 `SecurityFilterChain` Bean，自己定义规则；
2. 关掉表单登录与 httpBasic，否则未认证请求返回 302 而不是 401；
3. 放行登录接口，否则连登录都进不去。

### 4.3 配置

`application.yml`：

```yaml
server:
  port: 8009

spring:
  application:
    name: 09-认证与授权

app:
  jwt:
    secret: ${JWT_SECRET:backend-learning-2026-demo-jwt-secret-key-please-change}
    issuer: backend-learning
    expire-minutes: 120
```

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `app.jwt.secret` | 内置演示串，可用环境变量 `JWT_SECRET` 覆盖 | HS256 要求不短于 32 字节 |
| `app.jwt.issuer` | `backend-learning` | 签发方，解析时一并校验 |
| `app.jwt.expire-minutes` | `120` | 有效期，至少 1 分钟 |

密钥写成 `${JWT_SECRET:默认值}` 的形式，本地开发不用配环境变量，上线时用环境变量覆盖，不把真实密钥提交进仓库。

---

## 5. 动手实战

十个步骤，每一步都能编译、能验证。建议按顺序做，中途不要跳步，因为后面的步骤依赖前面的类。

### 步骤 1：统一响应与错误码

目标：先定好所有接口共用的返回结构，后面每个接口都复用它。

`common/Result.java`：

```java
@Data
public class Result<T> {

    private Integer code;
    private String message;
    private T data;

    private Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return new Result<>(code, message, null);
    }
}
```

`common/ErrorCode.java` 的错误码分两段：

| 段 | 取值 | 用途 |
| --- | --- | --- |
| 4xx/5xx | 400、401、403、404、500 | 与 HTTP 状态语义对齐 |
| 3xxxx | 30001 起 | 业务错误，账号相关 |

```java
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未认证或令牌已失效"),
    FORBIDDEN(403, "无权访问该资源"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "系统繁忙，请稍后再试"),

    BAD_CREDENTIALS(30001, "用户名或密码错误"),
    ACCOUNT_DISABLED(30002, "账号已被禁用"),
    USER_NOT_FOUND(30003, "用户不存在"),
    USERNAME_EXISTS(30004, "用户名已存在"),
    CANNOT_DELETE_SELF(30005, "不能删除当前登录账号");
}
```

本模块沿用 04-web 定下的约定：业务失败返回 HTTP 200，业务结果放在 `Result.code` 里；认证与授权失败则必须给真实的 401 与 403，因为前端拦截器靠状态码判断要不要跳登录页。

验证：这一步没有接口，`mvn -pl 09-security compile` 通过即可。

### 步骤 2：账号与角色

目标：把"角色带哪些权限"写成代码，后面所有鉴权都从这里取。

`model/Role.java`：

```java
public enum Role {

    ADMIN(Set.of("user:read", "user:write", "report:read")),
    USER(Set.of("report:read"));

    private final Set<String> permissions;

    Role(Set<String> permissions) {
        this.permissions = permissions;
    }

    public Set<String> permissions() {
        return permissions;
    }

    /**
     * hasRole("ADMIN") 实际比对的是 ROLE_ADMIN，前缀由 Security 补
     */
    public String authority() {
        return "ROLE_" + name();
    }

    /**
     * 角色摊平成权限字符串：角色名 + 该角色全部权限点
     */
    public static List<String> authorities(Collection<Role> roles) {
        return roles.stream()
                .flatMap(role -> Stream.concat(Stream.of(role.authority()), role.permissions().stream()))
                .distinct()
                .toList();
    }
}
```

`authorities` 方法做的是"角色 → 权限集合"的展开：一个用户有 ADMIN 角色，最终拿到的权限是 `ROLE_ADMIN`、`user:read`、`user:write`、`report:read` 四项。Security 只认 `GrantedAuthority`，不认我们的 `Role` 枚举，所以必须有这个转换。

`model/UserAccount.java` 是账号实体，字段为 `username`、`password`、`nickname`、`roles`、`enabled`。`password` 存的是密文，注释里要写明这一点，避免后来人往里塞明文。

讲解时强调两点：

- 权限点用“资源:动作”命名，前端和后端能对上同一套词，菜单和按钮可以直接按权限点显示。
- `enabled` 字段不是摆设，`guest` 账号靠它演示"密码对了但账号被禁用"这条分支。

### 步骤 3：密码与账号仓库

目标：密码只存密文，登录时用 BCrypt 比对。

`config/SecurityConfig.java` 里先给出编码器：

```java
/**
 * BCrypt 自带随机盐，同一密码两次加密结果不同，因此只能 matches 不能字符串比较
 */
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

BCrypt 有三个特点要讲：

| 特点 | 含义 | 影响 |
| --- | --- | --- |
| 自带随机盐 | 同一明文两次加密结果不同 | 不能把密文拷到另一个账号上用，也挡掉彩虹表 |
| 不可逆 | 只有加密没有解密 | 忘记密码只能重置，不能找回 |
| 故意算得慢 | 单次约几十毫秒 | 暴力破解成本高 |

仓库层用内存 Map，省掉数据库：

```java
@Repository
public class InMemoryUserRepository {

    private final Map<String, UserAccount> accounts = new ConcurrentHashMap<>();

    public InMemoryUserRepository(PasswordEncoder passwordEncoder) {
        seed(passwordEncoder);
    }

    private void seed(PasswordEncoder passwordEncoder) {
        save(new UserAccount("admin", passwordEncoder.encode("admin123"), "系统管理员", Set.of(Role.ADMIN), true));
        save(new UserAccount("teacher", passwordEncoder.encode("teacher123"), "陶老师", Set.of(Role.USER), true));
        save(new UserAccount("guest", passwordEncoder.encode("guest123"), "访客", Set.of(Role.USER), false));
    }
```

三个初始账号：

| 用户名 | 密码 | 角色 | 状态 | 用途 |
| --- | --- | --- | --- | --- |
| admin | admin123 | ADMIN | 启用 | 管理接口 |
| teacher | teacher123 | USER | 启用 | 普通用户视角 |
| guest | guest123 | USER | 禁用 | 验证禁用分支 |

验证：临时加一段测试或在 `main` 里打印两次 `passwordEncoder.encode("123456")`，两次结果不同，说明加盐生效。课堂演示用 jshell 或单元测试都行，看完删掉。

### 步骤 4：UserDetailsService 与 UserDetails

目标：把账号交给 Spring Security，让它能在登录和每次请求时取到用户。

`security/SecurityUser.java` 实现 `UserDetails`，构造时把角色摊平成权限：

```java
public class SecurityUser implements UserDetails {

    private final UserAccount account;
    private final List<GrantedAuthority> authorities;

    public SecurityUser(UserAccount account) {
        this.account = account;
        this.authorities = Role.authorities(account.getRoles()).stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }

    public UserAccount account() {
        return account;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return account.getPassword();
    }

    @Override
    public String getUsername() {
        return account.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return account.isEnabled();
    }
}
```

账号加载：

```java
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final InMemoryUserRepository repository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return repository.findByUsername(username)
                .map(SecurityUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在：" + username));
    }
}
```

讲解要点：

- Security 不认识我们的 `UserAccount`，只认 `UserDetails`。这个类就是适配层，换数据库时它不变。
- `isEnabled()` 返回 false 时，登录会抛 `DisabledException`，`guest` 账号靠这条分支演示。默认值为 true 的是 `isAccountNonExpired`、`isAccountNonLocked`、`isCredentialsNonExpired`，本模块照默认写。
- 返回 `UsernameNotFoundException` 而不是 null，Security 内部靠这个异常判断用户不存在。

### 步骤 5：登录校验

目标：不自己写密码比对，把校验交给 `AuthenticationManager`。

仍然在 `SecurityConfig` 里，两个 Bean：

```java
@Bean
public AuthenticationManager authenticationManager(UserDetailsService userDetailsService,
                                                  PasswordEncoder passwordEncoder) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
}
```

`ProviderManager` 持有一个或多个 `AuthenticationProvider`，请求进来后挨个问"你能处理这个认证吗"。`DaoAuthenticationProvider` 负责用户名密码这一类：先调 `UserDetailsService` 取用户，再检查账号是否禁用，最后用 `PasswordEncoder.matches` 比对密码。

三个异常分支要分清：

| 异常 | 触发条件 | 对外提示 |
| --- | --- | --- |
| `BadCredentialsException` | 密码不对，或用户不存在 | 30001 用户名或密码错误 |
| `DisabledException` | 账号 `enabled = false` | 30002 账号已被禁用 |
| `LockedException` | 账号被锁（本模块未用到） | 按业务定义 |

用户不存在时 `DaoAuthenticationProvider` 会把 `UsernameNotFoundException` 转成 `BadCredentialsException`，这正是我们想要的：对外只给一个提示，攻击者无法用登录接口枚举哪些用户名存在。

`service/AuthService.java` 的登录方法：

```java
public LoginResponse login(LoginRequest request) {
    Authentication authentication;
    try {
        authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
    } catch (DisabledException e) {
        log.warn("账号已禁用：{}", request.username());
        throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
    } catch (BadCredentialsException e) {
        log.warn("登录失败：{}", request.username());
        throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
    } catch (AuthenticationException e) {
        log.warn("登录异常：{}，原因：{}", request.username(), e.getMessage());
        throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
    }
    SecurityUser user = (SecurityUser) authentication.getPrincipal();
    // 校验通过后签发令牌，下一步实现
}
```

catch 的顺序不能反：`DisabledException` 也是 `AuthenticationException` 的子类，先捕父类会让禁用分支永远进不去。

验证：此时还没有接口，先用单元测试注入 `AuthenticationManager` 试一次：

```java
authenticationManager.authenticate(new UsernamePasswordAuthenticationToken("teacher", "teacher123"));
```

能返回 `Authentication` 说明账号与密码链路通了；换 `guest` 应抛 `DisabledException`。

### 步骤 6：签发与解析 JWT

目标：登录成功后签发令牌，后续请求能验出是谁。

先做配置绑定，`config/JwtProperties.java`：

```java
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank
        @Size(min = 32, message = "JWT 密钥至少 32 个字符（HS256 要求 256 bit）")
        String secret,

        @NotBlank
        String issuer,

        @Min(value = 1, message = "JWT 有效期至少 1 分钟")
        long expireMinutes
) {
}
```

record 直接绑定配置，构造器注入，不可变。`@Validated` 加上校验注解后，配置不合格会在启动阶段失败，早于任何一次请求。

`security/JwtTokenService.java`：

```java
@Service
public class JwtTokenService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final JwtProperties properties;
    private final SecretKey key;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issue(UserDetails user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.expireMinutes(), ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim("roles", rolesOf(user))
                // 固定 HS256，单参数版本会按密钥长度自动升级算法
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public String parseUsername(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
```

三个要点：

- `Keys.hmacShaKeyFor` 在密钥短于算法要求时直接抛 `WeakKeyException`。HS256 要求 32 字节，所以配置里用 `@Size(min = 32)` 提前拦住。
- `signWith(key)` 单参数版本会按密钥长度自动挑最强的 HMAC 算法：密钥超过 48 字节时签出来的是 HS384，令牌变长，`Header` 里写的是 `HS384`。要行为稳定就显式写 `Jwts.SIG.HS256`。
- `parseUsername` 一次做完三件事：验签名、校验 `iss`、校验 `exp`（过期抛 `ExpiredJwtException`）。不用自己判断时间。

验证：把上面 `AuthService` 的登录方法补完：

```java
SecurityUser user = (SecurityUser) authentication.getPrincipal();
JwtTokenService.IssuedToken issued = jwtTokenService.issue(user);
return new LoginResponse(issued.token(), "Bearer", issued.expiresAt(),
        user.getUsername(), user.roleNames());
```

登录接口 `controller/AuthController.java`：

```java
@PostMapping("/login")
public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
    return Result.ok(authService.login(request));
}
```

此时启动应用，`POST /auth/login` 能返回令牌。把令牌复制到任意 Base64URL 解码工具里，能看到三段内容与第 3.3 节一致。

### 步骤 7：认证过滤器

目标：每个请求带令牌时，把它换成 Security 认识的身份。

`security/JwtAuthenticationFilter.java`：

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService tokenService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(request, token);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            String username = tokenService.parseUsername(token);
            UserDetails user = userDetailsService.loadUserByUsername(username);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | UsernameNotFoundException e) {
            SecurityContextHolder.clearContext();
            log.debug("令牌校验未通过：{}", e.getMessage());
        }
    }
```

三个设计决定，讲课时逐个解释：

第一，`OncePerRequestFilter` 保证一次请求只跑一遍，转发和 include 不会重复执行。

第二，令牌无效时不在这里写响应，只清空上下文继续走。后面授权判断会拒绝匿名请求，401 由 `RestAuthenticationEntryPoint` 统一给。如果在过滤器里自己写 401，同一个错误就有了两种响应格式，前端要写两套判断。

第三，这个类不加 `@Component`，由 `SecurityConfig` 内部 `new`。加了 `@Component` 之后它同时是 Bean，Spring Boot 会把它注册成 Servlet 过滤器，结果一次请求跑两遍：一遍在 Servlet 层，一遍在 Security 链上。功能上看不出问题，只是白跑一遍，但排查时很难想到。

第二个参数 `null` 是密码。认证已经完成，不需要再持有凭据，把它丢掉可以避免密码在内存里长时间存在。

验证：写一个最小受保护接口 `GET /security/hello`，用带令牌的请求访问，能拿到当前用户名。

### 步骤 8：过滤链配置

目标：定义哪些接口放行、哪些要登录、哪些要管理员。

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenService jwtTokenService,
                                                   UserDetailsService userDetailsService,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        JwtAuthenticationFilter jwtFilter = new JwtAuthenticationFilter(jwtTokenService, userDetailsService);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login", "/public/**", "/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

逐行解释：

| 配置 | 作用 | 不写会怎样 |
| --- | --- | --- |
| `csrf disable` | 关闭跨站请求伪造校验 | 纯 API 用 Bearer 令牌没有 cookie，POST 会莫名 403 |
| `httpBasic / formLogin disable` | 关掉默认的两种登录方式 | 未认证请求返回 302 跳登录页，前端拿不到 401 |
| `logout disable` | 关掉默认退出接口 | 与自己的接口路径冲突，且无状态场景下它只清 session |
| `STATELESS` | 不创建 session | 每个请求都新建 session，浪费内存 |
| `permitAll` 三条 | 登录、开放接口、错误页放行 | 连登录接口都要认证，永远登不进去 |
| `hasRole("ADMIN")` | 管理接口整组限管理员 | 普通用户能调管理接口 |
| `anyRequest().authenticated()` | 其余全部要登录，必须最后写 | 写在前面的规则会让后面全部失效 |
| `exceptionHandling` | 指定 401/403 的响应实现 | 返回 Spring 默认空白页 |
| `addFilterBefore` | 把 JWT 过滤器插进链 | 令牌不起作用，全部请求都是匿名 |

规则顺序是硬约束：`authorizeHttpRequests` 从上往下匹配，命中即停。把 `anyRequest()` 写到前面，后面的 `permitAll` 与 `hasRole` 全部作废，这是学生作业里最常见的错。

验证：

```bash
curl -i http://127.0.0.1:8009/security/hello
```

期望 `401`。若得到 `302`，检查 `formLogin` 是否确实关闭。

### 步骤 9：401 与 403 的响应体

目标：让两个失败场景都返回统一的 JSON。

`security/RestErrorWriter.java` 负责写：

```java
@Component
public class RestErrorWriter {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, HttpStatus status, Result<Void> body) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
```

这里注入的 `ObjectMapper` 是 Spring Boot 4 自动配置的 Jackson 3 实例（包名 `tools.jackson.databind`），与项目其他地方序列化结果一致。

两个处理器分别对应两种失败：

```java
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        errorWriter.write(response, HttpStatus.UNAUTHORIZED, Result.fail(ErrorCode.UNAUTHORIZED));
    }
}
```

```java
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        errorWriter.write(response, HttpStatus.FORBIDDEN, Result.fail(ErrorCode.FORBIDDEN));
    }
}
```

方法级鉴权的失败由全局异常处理器接：

```java
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException e) {
    log.warn("方法级鉴权拒绝：{}", e.getMessage());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Result.fail(ErrorCode.FORBIDDEN));
}
```

这里返回 `ResponseEntity` 而不是裸 `Result`，因为必须把 HTTP 状态码设成 403。若沿用其他业务异常的写法返回 `Result`，HTTP 状态会是 200，前端拦截器会把"没有权限"当成成功响应，跳转逻辑全乱。

验证：

```bash
curl -i http://127.0.0.1:8009/security/hello
```

期望：

```
HTTP/1.1 401
{"code":401,"data":null,"message":"未认证或令牌已失效"}
```

### 步骤 10：接口与四种粒度的授权

目标：把四种鉴权粒度各写一个例子，让学生看清适用场景。

| 粒度 | 写法 | 本模块示例 | 适用场景 |
| --- | --- | --- | --- |
| 按 URL | `requestMatchers("/admin/**").hasRole("ADMIN")` | 管理接口整组 | 一整组接口同一门槛 |
| 按角色 | `@PreAuthorize("hasRole('ADMIN')")` | `/api/reports/admin-summary` | 少数接口单独提权 |
| 按权限点 | `@PreAuthorize("hasAuthority('report:read')")` | `/api/reports` | 同一接口给多类人用 |
| 按数据归属 | `@PreAuthorize("#owner == authentication.name or hasRole('ADMIN')")` | `/api/reports/{owner}` | 只能看自己的数据 |

管理接口的控制器：

```java
@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

    @GetMapping
    public Result<List<UserProfile>> list() {
        return Result.ok(userAdminService.list());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('user:write')")
    public Result<UserProfile> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.ok(userAdminService.create(request));
    }

    @DeleteMapping("/{username}")
    public Result<Void> delete(@PathVariable String username, @AuthenticationPrincipal SecurityUser user) {
        userAdminService.delete(username, user.getUsername());
        return Result.ok();
    }
}
```

类上一条 `hasRole('ADMIN')`，写接口再叠一条 `hasAuthority('user:write')`。这是双重把关：将来有人把 URL 规则放宽成 `/admin/**` 只要登录，写接口仍然是拒绝的。

报表接口演示剩下两种粒度：

```java
@GetMapping
@PreAuthorize("hasAuthority('report:read')")
public Result<List<String>> list() {
    return Result.ok(List.of("2026 秋季学期班级周报", "课程实训进度月报"));
}

@GetMapping("/{owner}")
@PreAuthorize("#owner == authentication.name or hasRole('ADMIN')")
public Result<String> byOwner(@PathVariable String owner) {
    return Result.ok(owner + " 的报告：本周一切正常");
}
```

`#owner` 是方法参数名，`authentication.name` 是当前登录用户名。这行表达式依赖编译时保留参数名，父 POM 已开 `-parameters`；自己新建工程时若这段不生效，先查编译器参数，不要怀疑注解。

`@AuthenticationPrincipal SecurityUser user` 直接拿当前用户。它取的是 `SecurityContext` 里的 principal，也就是过滤器放进去的 `SecurityUser`，因此能取到 `nickname` 这类令牌里没有的字段。

验证：用 teacher 令牌访问 `/api/reports/teacher` 返回 200，访问 `/api/reports/admin` 返回 403。

---

## 6. 文件清单与职责

| 包 | 文件 | 职责 | 换数据库要改吗 |
| --- | --- | --- | --- |
| `common` | `Result`、`ErrorCode`、`BusinessException`、`GlobalExceptionHandler` | 统一响应与异常兜底 | 否 |
| `config` | `SecurityConfig` | 过滤链、认证管理器、密码编码器 | 否 |
| `config` | `JwtProperties` | JWT 配置绑定与校验 | 否 |
| `model` | `UserAccount`、`Role` | 账号与角色权限映射 | `UserAccount` 对应实体，要 |
| `repository` | `InMemoryUserRepository` | 账号存储 | 要，改这里即可 |
| `security` | `SecurityUser` | `UserDetails` 适配 | 否 |
| `security` | `JwtTokenService` | 签发与解析令牌 | 否 |
| `security` | `JwtAuthenticationFilter` | 令牌换身份 | 否 |
| `security` | `RestErrorWriter`、`RestAuthenticationEntryPoint`、`RestAccessDeniedHandler` | 401/403 响应 | 否 |
| `service` | `AccountUserDetailsService` | 账号加载 | 否 |
| `service` | `AuthService` | 登录 | 否 |
| `service` | `UserAdminService` | 账号增删查 | 否 |
| `dto` | `LoginRequest`、`CreateUserRequest` | 入参校验 | 否 |
| `vo` | `LoginResponse`、`UserProfile` | 出参白名单 | 否 |
| `controller` | `AuthController`、`PublicController`、`SecurityController`、`UserAdminController`、`ReportController` | 接口 | 否 |

`vo/UserProfile.java` 只带 `username`、`nickname`、`roles`、`authorities`、`enabled`，没有 `password`。出参对象不带敏感字段，比在每个接口里记得别返回密码更可靠。

---

## 7. 运行与验证

```bash
mvn -pl 09-security spring-boot:run
```

端口 `8009`。下面 11 条按顺序执行，覆盖放行、未认证、登录、越权、数据归属、禁用账号：

| # | 请求 | 状态码 | 实测响应 |
| --- | --- | --- | --- |
| 1 | `GET /public/ping` | 200 | `{"code":200,"data":{"message":"无需认证即可访问","status":"ok"},"message":"success"}` |
| 2 | `GET /security/hello` 无令牌 | 401 | `{"code":401,"data":null,"message":"未认证或令牌已失效"}` |
| 3 | `POST /auth/login` admin | 200 | `{"code":200,"data":{"token":"eyJ...","tokenType":"Bearer","expiresAt":"...","username":"admin","roles":["ADMIN"]},"message":"success"}` |
| 4 | `GET /security/hello` 带 admin 令牌 | 200 | `{"code":200,"data":"认证与授权 模块，当前用户：admin","message":"success"}` |
| 5 | `GET /auth/me` 带 teacher 令牌 | 200 | `{"code":200,"data":{"username":"teacher","nickname":"陶老师","roles":["USER"],"authorities":["ROLE_USER","report:read"],"enabled":true},"message":"success"}` |
| 6 | `GET /admin/users` 带 teacher 令牌 | 403 | `{"code":403,"data":null,"message":"无权访问该资源"}` |
| 7 | `GET /admin/users` 带 admin 令牌 | 200 | 三个账号列表，每项无 password 字段 |
| 8 | `GET /api/reports` 带 teacher 令牌 | 200 | 报告列表数组 |
| 9 | `GET /api/reports/admin-summary` 带 teacher 令牌 | 403 | `{"code":403,"data":null,"message":"无权访问该资源"}` |
| 10 | `GET /api/reports/admin` 带 teacher 令牌 | 403 | 数据归属拦截 |
| 11 | `POST /auth/login` guest | 200 | `{"code":30002,"data":null,"message":"账号已被禁用"}` |

第 11 条的 HTTP 状态是 200，业务结果在 `code` 里。这是本仓库 04-web 定下的约定，与第 2 条真实 401 的语义不同：前者是业务失败，后者是协议层要求。

命令行复现：

```bash
# 取管理员令牌
TOKEN=$(curl -s -X POST http://127.0.0.1:8009/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')

# 带令牌访问
curl -s -H "Authorization: Bearer $TOKEN" http://127.0.0.1:8009/auth/me
```

手工解码令牌：

```bash
# 第二段做 Base64URL 解码，注意补齐等号
echo '<payload 段>' | base64 -d
```

Windows 下用 PowerShell 的 `[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String(...))`，Base64URL 的 `-` 与 `_` 要先换回 `+` 与 `/`。

---

## 8. 自动化测试

```bash
mvn -pl 09-security test
```

`SecurityApiTest` 有 14 个用例：

| 用例 | 覆盖点 |
| --- | --- |
| `publicEndpointIsOpen` | `permitAll` 生效 |
| `anonymousIsRejected` | 未认证返回 401，两个受保护路径都试 |
| `loginIssuesToken` | 登录返回令牌、类型、过期时间、角色 |
| `loginWithBadCredentials` | 密码错误与用户不存在给同一个业务码 |
| `loginWithDisabledAccount` | 禁用账号返回 30002 |
| `loginValidatesInput` | 缺参数返回 400 与字段级提示 |
| `tokenGrantsAccess` | 令牌换到身份，响应不含密码 |
| `tamperedTokenIsRejected` | 篡改令牌按未认证处理 |
| `userCannotReachAdminArea` | URL 规则返回 403 |
| `adminCanListUsers` | 管理员可查列表 |
| `methodSecurityWorks` | 权限点放行、角色拒绝 |
| `dataOwnershipIsChecked` | 只能看自己的数据，管理员不受限 |
| `createdUserCanLogin` | 新增用户后能用新密码登录 |
| `cannotDeleteSelf` | 禁止删除当前账号 |

课上有时间就带学生看两个用例的写法：一个是 `tokenGrantsAccess`，演示怎么从登录响应里取令牌再带到下一个请求；另一个是 `methodSecurityWorks`，演示同一个令牌访问两个接口得到 200 与 403。

测试报告在 `09-security/target/surefire-reports/`，失败时先看这里的具体断言，不要靠猜。

---

## 9. 排错手册

| 现象 | 日志或响应特征 | 原因 | 处理 |
| --- | --- | --- | --- |
| 所有接口 401，登录也进不去 | 登录接口也返回 401 | 登录路径没放行 | `permitAll` 加 `/auth/login`，且写在 `anyRequest` 之前 |
| 未认证返回 302 | 响应头有 `Location: /login` | `formLogin` 没关 | `formLogin(AbstractHttpConfigurer::disable)` |
| 401 响应体是空白 HTML | 响应体是 Spring 默认错误页 | 没配 `authenticationEntryPoint` | 指定自己的 `RestAuthenticationEntryPoint` |
| `hasRole("ADMIN")` 永不通过 | 403，权限集合里是 `ADMIN` | 少了 `ROLE_` 前缀 | 用 `Role.authority()` 统一生成 |
| 带令牌仍 403 | 令牌正常但权限不够 | 角色权限点没给到 | 访问 `/security/authorities` 看实际权限集合 |
| 带令牌仍 401 | 调试日志有"令牌校验未通过" | 令牌过期、被改、或密钥不一致 | 解码看 `exp`；确认签发与解析同一个 `SecretKey` |
| 启动报 WeakKeyException | 堆栈指向 `Keys.hmacShaKeyFor` | 密钥短于 32 字节 | 换长密钥，`@Size` 会提前拦 |
| 令牌 Header 里是 HS384 | 解码看到 `{"alg":"HS384"}` | 用了 `signWith(key)` 单参数版 | 显式 `signWith(key, Jwts.SIG.HS256)` |
| 启动日志警告 UserDetailsService 不参与自动配置 | `InitializeUserDetailsManagerConfigurer` 输出 WARN | 容器里有 `AuthenticationProvider` Bean | 在 `AuthenticationManager` 内部构造 provider，不单独暴露 |
| 过滤器逻辑执行两遍 | 断点或日志出现两次 | 过滤器既是 Bean 又被加进链 | 去掉 `@Component`，配置类里 `new` |
| `@PreAuthorize` 完全不生效 | 无权限也能访问 | 少 `@EnableMethodSecurity` | 加在配置类上 |
| 方法级鉴权返回 403 但响应是 200 | HTTP 200 且 `code` 为 403 | 处理器返回了裸 `Result` | 用 `ResponseEntity.status(403)` |
| SpEL 里 `#owner` 取到 null | 表达式恒为 false | 编译没保留参数名 | 确认编译参数 `-parameters`，改完重新编译 |

课堂上演示两个最容易复现的：把 `anyRequest()` 提到最前面，看规则失效；把过滤器加上 `@Component`，在 `doFilterInternal` 里打日志，看一次请求两条日志。

---

## 10. 课堂练习与参考答案

### 练习 1：把 `/public/**` 改成需要登录（3 分钟）

改 `SecurityConfig`，去掉 `permitAll` 里的 `/public/**`，重启后访问 `/public/ping`。

答案：期望 401。这一步让学生体会规则是按顺序匹配的，删掉一条放行规则即可改变行为，不需要改 Controller。

### 练习 2：给 USER 角色补 `user:read`（5 分钟）

在 `Role.USER` 里加 `"user:read"`，然后：

```java
@GetMapping("/summary")
@PreAuthorize("hasAuthority('user:read')")
```

答案：加了权限点后普通用户能访问，但 `POST /admin/users` 仍要 `user:write`，仍然 403。这说明权限点比角色细，能把"读"与"写"分开。

### 练习 3：清空 USER 的权限点（3 分钟）

把 `USER(Set.of("report:read"))` 改成 `USER(Set.of())`，重启后用 teacher 令牌访问 `/api/reports`。

答案：返回 403，管理员不受影响。因为管理员走的是 ADMIN 角色的权限集合，与 USER 无关。这里可以顺势问：如果一个用户同时有两个角色会怎样？答案是权限取并集。

### 练习 4：观察 BCrypt 加盐（4 分钟）

```java
@Test
void bcryptSaltsEveryTime() {
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    String first = encoder.encode("123456");
    String second = encoder.encode("123456");
    System.out.println(first);
    System.out.println(second);
    assertThat(first).isNotEqualTo(second);
    assertThat(encoder.matches("123456", first)).isTrue();
}
```

答案：两次输出完全不同，约 60 个字符，以 `$2a$` 开头，中间那段是盐。但仍然能 `matches` 通过，说明盐是存在密文里的。这一段讲完，学生就明白为什么不能用 `equals` 比密码。

---

## 11. 课后作业与评分点

### 基础：登录失败次数限制（20 分）

要求：同一用户名连续失败 5 次，锁定 10 分钟；锁定期间登录返回新业务码；成功登录后计数清零。

提示：用 `ConcurrentHashMap<String, Integer>` 与时间戳记录，写在 `AuthService` 里；在课堂上能跑通即可，但要说明换到 Redis 后怎么改。

评分点：

| 项 | 分值 | 标准 |
| --- | --- | --- |
| 计数与清零 | 6 | 失败累加、成功清零，边界正确 |
| 锁定判断 | 6 | 到期自动解锁，不依赖定时任务 |
| 业务码与提示 | 4 | 新增错误码，不泄露账号是否存在 |
| 说明换 Redis 的改法 | 4 | 讲清 key 设计、过期时间、原子性 |

### 进阶：账号存数据库（30 分）

要求：用 MyBatis-Plus 建 `t_user`、`t_role`、`t_user_role` 三张表，替换 `InMemoryUserRepository`，`UserDetailsService` 与认证流程不改。

评分点：

| 项 | 分值 | 标准 |
| --- | --- | --- |
| 表结构与初始数据 | 8 | 角色可多对多，密码列存密文 |
| 仓储实现 | 10 | 查询走参数化，无字符串拼接 |
| 上层零改动 | 8 | 认证与鉴权代码未修改 |
| 测试 | 4 | 至少覆盖登录成功与禁用账号两条 |

### 挑战：刷新令牌与登出（30 分）

要求：实现刷新令牌；改密码或点退出后，旧令牌立刻失效。

提示：两条路线。令牌版本号存用户表，签发时写入，校验时比对；或者维护黑名单，登出时把 `jti` 放进去并设置与令牌剩余有效期一致的过期时间。

评分点：

| 项 | 分值 | 标准 |
| --- | --- | --- |
| 方案说明 | 8 | 讲清两种路线的代价与适用场景 |
| 刷新流程 | 10 | 刷新令牌与访问令牌分离，刷新令牌可撤销 |
| 失效验证 | 8 | 改密码后旧访问令牌立即 401，有测试证明 |
| 存储与过期 | 4 | 黑名单或版本号不无限增长 |

---

## 12. 小结与知识地图

```
认证 ──┬── 口令校验 ── BCrypt + AuthenticationManager + UserDetailsService
       └── 令牌签发 ── JwtTokenService（sub / iss / iat / exp）
                                   │
授权 ──┬── 令牌验签 ── JwtAuthenticationFilter → SecurityContext
       ├── URL 规则 ── SecurityFilterChain.requestMatchers
       ├── 方法规则 ── @PreAuthorize（角色 / 权限点 / 数据归属）
       └── 失败响应 ── EntryPoint(401) / AccessDeniedHandler(403) / 全局异常(403)
```

| 知识点 | 一句话回顾 |
| --- | --- |
| 认证与授权 | 你是谁、你能做什么，对应 401 与 403 |
| 无状态令牌 | 服务端不存会话，验签即认身份 |
| JWT 三段 | Header 算法、Payload 明文载荷、Signature 防篡改 |
| `SecurityFilterChain` | 组件式配置唯一入口，规则从具体排到宽泛 |
| `UserDetailsService` | 账号来源的抽象，换库改实现 |
| BCrypt | 加盐不可逆，只 `matches` 不比字符串 |
| RBAC | 用户挂角色，角色带权限点，接口绑权限点 |
| 失败响应两条路径 | 过滤器层自己写，Controller 层交全局异常 |

下一节 [10-file](10-file.md) 讲文件上传与下载。

> 本节遵循的规范（Spring 官方文档 + 阿里巴巴 Java 开发手册）：用 `SecurityFilterChain` 组件式配置，不用已移除的 `WebSecurityConfigurerAdapter`；密码用 BCrypt 存储，禁止明文与 MD5；密钥走配置与环境变量，不硬编码；令牌只承载身份，权限回查；敏感接口最小授权并叠加方法级校验；登录失败不区分用户是否存在。
