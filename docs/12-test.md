# 12 · 测试 —— 用 JUnit5 保证代码质量

> - 难度：进阶
> - 前置模块：04-web
> - 预计时长：约 45 分钟
> - 对应代码：[12-test](https://github.com/mqxu/backend-learning/tree/main/12-test)

## 1. 本节导读

学完本节，你将能：

1. 用 JUnit5 写单元测试与断言；
2. 用 MockMvc 测试接口，无需真实起服务；
3. 理解单元测试与集成测试的区别。

**前置知识**：会 `04-web`。

---

## 2. 概念铺垫

### 2.1 为什么要写测试

改了 A 却弄坏了 B，是开发中最怕的事。测试就是「自动化的验证」：每次改动跑一遍，立刻知道有没有破坏已有功能。

### 2.2 单元测试 vs 集成测试

| 类型 | 范围 | 特点 |
| --- | --- | --- |
| 单元测试 | 单个方法/类 | 快、隔离、不依赖外部 |
| 集成测试 | 多个组件协作 | 需要容器/数据库，较慢 |

### 2.3 MockMvc 是什么

真实测试接口通常要起服务、发 HTTP 请求，很麻烦。`MockMvc` 在**不启动服务器**的情况下，直接模拟请求打到 controller，快速验证接口行为。

---

## 3. 环境准备

父 POM 已提供 `spring-boot-starter-webmvc-test`（包含 JUnit5、MockMvc、Spring Test）。测试代码放在 `src/test/java`。

---

## 4. 动手实战

> 本模块当前是骨架，下面带你逐步补全测试。

### 步骤 1：单元测试

测试一个工具类/业务方法：

```java
package top.mqxu.test.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 计算服务测试
 *
 * @author mqxu
 */
class CalculatorTest {

    private final Calculator calculator = new Calculator();

    @Test
    void add_shouldReturnSum() {
        assertEquals(5, calculator.add(2, 3));
    }

    @Test
    void divide_shouldThrowWhenZero() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.divide(10, 0));
    }
}
```

被测类：

```java
package top.mqxu.test.service;

/**
 * 计算服务
 *
 * @author mqxu
 */
public class Calculator {

    public int add(int a, int b) {
        return a + b;
    }

    public int divide(int a, int b) {
        if (b == 0) {
            throw new IllegalArgumentException("除数不能为 0");
        }
        return a / b;
    }
}
```

讲解：

- `@Test` 标注测试方法；
- `assertEquals` 断言相等，`assertThrows` 断言抛异常；
- 测试方法名用 `行为_条件_期望` 风格，见名知意。

### 步骤 2：MockMvc 测试接口

```java
package top.mqxu.test.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 接口测试
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class HelloControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void hello_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("hello world"));
    }
}
```

讲解：

- `@SpringBootTest` 拉起 Spring 上下文；`@AutoConfigureMockMvc` 提供 MockMvc；
- `perform(get(...))` 发请求，`andExpect(...)` 断言状态码与内容。

### 步骤 3：参数化测试

目标：同一逻辑测多组数据。

```java
@ParameterizedTest
@CsvSource({"1,2,3", "10,20,30", "-1,1,0"})
void add_shouldReturnSum(int a, int b, int expected) {
    assertEquals(expected, calculator.add(a, b));
}
```

讲解：`@CsvSource` 每行一组入参，`@ParameterizedTest` 依次执行，省去重复代码。

---

## 5. 完整代码与目录结构

```
12-test
└── src
    ├── main/java/top/mqxu/test
    │   ├── TestApplication.java
    │   ├── controller/HelloController.java
    │   └── service/Calculator.java
    └── test/java/top/mqxu/test
        ├── service/CalculatorTest.java
        └── controller/HelloControllerTest.java
```

---

## 6. 运行与验证

```bash
mvn -pl 12-test -am test
```

输出应包含 `BUILD SUCCESS`，并列出通过的测试数量。

---

## 7. 常见问题与避坑

| 现象 | 原因 | 解决 |
| --- | --- | --- |
| 测试没执行 | 方法不是 `public` 或没加 `@Test` | 加 `@Test`，JUnit5 方法可包私有 |
| MockMvc 空指针 | 没加 `@AutoConfigureMockMvc` | 补注解 |
| 断言不过 | 期望值写错 | 核对实际返回值 |
| 测试类找不到 | 类名不是 `*Test` | 按 `*Test` / `Test*` 命名，或在 pom 配置 surefire |

---

## 8. 练习

### 8.1 课堂随堂练习（课上约 10 分钟）

| # | 练习 | 验证点 |
| --- | --- | --- |
| 1 | 给 `Calculator` 加 `subtract` 并写测试 | 测试通过 |
| 2 | 写一个 `divide` 除 0 抛异常的测试 | `assertThrows` 通过 |
| 3 | 用 MockMvc 测试一个 GET 接口 | 状态码 200 |

### 8.2 课后练习（课后完成并提交）

- **基础**：给 `/hello` 加一个带 `name` 参数分支，写两组 MockMvc 测试。
- **进阶**：用 `@ParameterizedTest` 给 `add` 补 5 组边界数据。
- **挑战**：给一个 Service 方法做「数据库依赖 mock」的单元测试（`@MockitoBean`），写一段话说明 mock 解决了什么问题。

---

## 9. 小结与知识地图

| 知识点 | 一句话回顾 |
| --- | --- |
| JUnit5 | `@Test` + 断言 |
| MockMvc | 不起服务，模拟请求测接口 |
| `@ParameterizedTest` | 多组数据一次测 |
| 单元 vs 集成 | 隔离快 vs 全链路慢 |

下一节 [13-actuator](13-actuator.md) 会讲应用监控。

> 本节遵循的规范（官方 + 阿里手册）：测试方法命名清晰（行为_条件_期望）；核心逻辑必须有单测；断言明确、不写 `System.out` 代替断言。
