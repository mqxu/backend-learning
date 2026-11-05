package top.mqxu.doc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 把生成的 OpenAPI JSON 当契约来测。
 *
 * <p>注解写错了不会报错，接口照常工作，只是文档里那句话错了。所以这些断言检查的不是"接口能不能用"，
 * 而是"文档里该有的东西有没有"。改动接口或注解时跑一遍，能挡住"改了代码忘了改文档"。</p>
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDocumentTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("完整文档包含元信息与全部接口")
    void documentHasInfoAndPaths() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("图书管理接口"))
                .andExpect(jsonPath("$.info.version").value("1.0.0"))
                .andExpect(jsonPath("$.paths").value(hasKey("/books")))
                .andExpect(jsonPath("$.paths").value(hasKey("/books/{id}")))
                .andExpect(jsonPath("$.servers").isArray());
    }

    @Test
    @DisplayName("book 分组只收 /books 下的接口")
    void bookGroupIsolated() throws Exception {
        mockMvc.perform(get("/v3/api-docs/book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths").value(hasKey("/books")))
                .andExpect(jsonPath("$.paths").value(org.hamcrest.Matchers.not(hasKey("/ops/health"))));
    }

    @Test
    @DisplayName("ops 分组收健康检查")
    void opsGroupContainsHealth() throws Exception {
        mockMvc.perform(get("/v3/api-docs/ops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths").value(hasKey("/ops/health")));
    }

    @Test
    @DisplayName("接口说明来自 @Operation，不是方法名")
    void summaryComesFromAnnotation() throws Exception {
        mockMvc.perform(get("/v3/api-docs/book"))
                .andExpect(jsonPath("$['paths']['/books']['get']['summary']").value("分页查询图书"))
                .andExpect(jsonPath("$['paths']['/books']['post']['summary']").value("新增图书"));
    }

    @Test
    @DisplayName("@ParameterObject 把查询对象摊平成独立参数")
    void queryObjectFlattened() throws Exception {
        mockMvc.perform(get("/v3/api-docs/book"))
                .andExpect(jsonPath("$['paths']['/books']['get']['parameters']").isArray())
                .andExpect(jsonPath("$['paths']['/books']['get']['parameters'][*].name")
                        .value(hasItem("pageNum")))
                .andExpect(jsonPath("$['paths']['/books']['get']['parameters'][*].name")
                        .value(hasItem("keyword")));
    }

    @Test
    @DisplayName("枚举值进文档，调用方不用猜有哪些分类")
    void categoryEnumDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs/book"))
                // 枚举没有单独的 schema，是内联在用到它的字段上
                .andExpect(jsonPath("$.components.schemas.Book.properties.category.enum")
                        .value(containsInAnyOrder("TECH", "LITERATURE", "HISTORY", "SCIENCE", "OTHER")))
                .andExpect(jsonPath("$.components.schemas.Book.properties.category.description")
                        .value("分类"));
    }

    @Test
    @DisplayName("必填字段被标成 required")
    void requiredFieldsMarked() throws Exception {
        mockMvc.perform(get("/v3/api-docs/book"))
                .andExpect(jsonPath("$.components.schemas.BookCreateRequest.required")
                        .value(hasItem("title")))
                .andExpect(jsonPath("$.components.schemas.BookCreateRequest.required")
                        .value(hasItem("isbn")))
                .andExpect(jsonPath("$.components.schemas.BookCreateRequest.required")
                        .value(hasItem("category")));
    }

    @Test
    @DisplayName("字段的示例值也在文档里")
    void fieldExamplesPresent() throws Exception {
        mockMvc.perform(get("/v3/api-docs/book"))
                .andExpect(jsonPath("$.components.schemas.BookCreateRequest.properties.isbn.example")
                        .value("9787111641247"));
    }

    @Test
    @DisplayName("只读字段标了 readOnly，前端不会把它提交上来")
    void readOnlyFieldsMarked() throws Exception {
        mockMvc.perform(get("/v3/api-docs/book"))
                .andExpect(jsonPath("$.components.schemas.Book.properties.id.readOnly").value(true))
                .andExpect(jsonPath("$.components.schemas.Book.properties.createTime.readOnly").value(true));
    }

    @Test
    @DisplayName("响应码说明来自 @ApiResponse")
    void responseCodesDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs/book"))
                .andExpect(jsonPath("$['paths']['/books']['post']['responses']")
                        .value(hasKey("200")))
                .andExpect(jsonPath("$['paths']['/books']['post']['responses']")
                        .value(hasKey("400")))
                .andExpect(jsonPath("$['paths']['/books']['post']['responses']")
                        .value(hasKey("409")));
    }
}
