# 10-doc · 接口文档

> 📖 详细教程：[docs/10-doc.md](../docs/10-doc.md)

用 springdoc 从代码生成 OpenAPI 文档，Swagger UI 直接调接口。文档随代码变，不用手工维护。

## 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/books` | 分页查询（pageNum、pageSize、keyword、category） |
| GET | `/books/{id}` | 查询详情 |
| POST | `/books` | 新增，ISBN 唯一 |
| PUT | `/books/{id}` | 全量更新 |
| DELETE | `/books/{id}` | 删除 |
| GET | `/books/categories` | 分类枚举 |
| GET | `/ops/health` | 健康检查（运维分组） |
| GET | `/v3/api-docs` | 完整 OpenAPI JSON |
| GET | `/v3/api-docs/book` | 图书分组 JSON |
| GET | `/swagger-ui.html` | 文档页面 |

## 运行

```bash
mvn -pl 10-doc spring-boot:run
# 浏览器打开 http://127.0.0.1:8010/swagger-ui.html
node tools/run-cases.mjs 10-doc
```

## 测试

```bash
mvn -pl 10-doc test
```

18 个用例，两个类：

| 测试类 | 用例数 | 说明 |
| --- | --- | --- |
| `OpenApiDocumentTest` | 10 | 把生成的 JSON 当契约：元信息、分组隔离、summary 来自注解、参数摊平、枚举、required、readOnly、响应码 |
| `BookApiTest` | 8 | 接口行为：分页、过滤、404、新增、ISBN 重复 409、校验 400、删除 |

注解写错不会报错，接口照常工作，只是文档错了。文档测试就是用来挡这个的。

## 版本对应

| Spring Boot | springdoc |
| --- | --- |
| 3.x | 2.x |
| 4.x | 3.x（本模块用 3.2.0） |

knife4j 4.5.0 依赖 springdoc 2.x，在 Boot 4 上不兼容，本模块用原生 Swagger UI。

## 要点

- `@ParameterObject` 把查询参数 POJO 展开成独立参数，漏了会显示成一个对象
- 枚举的每个值加 `@Schema`，文档里才是下拉框而不是裸词
- 生产环境关掉 `springdoc.api-docs.enabled`，别暴露内部接口结构
- 文档 JSON 可直接导入 Apifox，导入说明见 [docs/apifox](../docs/apifox/README.md)
