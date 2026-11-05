package top.mqxu.doc;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 图书接口的行为，与文档无关。和 OpenApiDocumentTest 分开：一个测"接口怎么工作"，一个测"文档写了什么"。
 *
 * @author mqxu
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookApiTest {

    /** 与种子数据里的《深入理解 Java 虚拟机》同一个 ISBN */
    private static final String EXISTING_ISBN = "9787111641247";

    @Autowired
    private MockMvc mockMvc;

    private String createBook() throws Exception {
        // 每次生成不同的 13 位 ISBN，重复跑测试不会撞上"ISBN 已存在"
        String isbn = "978" + String.format("%010d", Math.abs(System.nanoTime()) % 10_000_000_000L);
        String body = mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "重构：改善既有代码的设计",
                                  "author": "Martin Fowler",
                                  "isbn": "%s",
                                  "category": "TECH",
                                  "price": 118.00,
                                  "stock": 15,
                                  "publishDate": "2019-05-01"
                                }""".formatted(isbn)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        // 先落到 Object：JsonPath.read 的泛型会被 String.valueOf 的 char[] 重载带偏，
        // 推断成 char[] 后在运行期抛 ClassCastException
        Object id = JsonPath.read(body, "$.data.id");
        return String.valueOf(id);
    }

    @Test
    @DisplayName("分页查询返回统一结构：list 与 total")
    void pageReturnsUnifiedShape() throws Exception {
        mockMvc.perform(get("/books").param("pageNum", "1").param("pageSize", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list", hasSize(3)))
                .andExpect(jsonPath("$.data.total", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(3));
    }

    @Test
    @DisplayName("关键字只匹配书名或作者")
    void keywordFilters() throws Exception {
        String body = mockMvc.perform(get("/books").param("keyword", "Java"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        java.util.List<String> titles = JsonPath.read(body, "$.data.list[*].title");
        assertThat(titles).isNotEmpty().allMatch(t -> t.contains("Java"));
    }

    @Test
    @DisplayName("查不到的 id 返回 404 与业务码 20404")
    void missingBookReturns404() throws Exception {
        mockMvc.perform(get("/books/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(20404));
    }

    @Test
    @DisplayName("新增成功，返回带 id 的完整对象")
    void createReturnsFullObject() throws Exception {
        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "设计模式",
                                  "author": "GoF",
                                  "isbn": "9787111075752",
                                  "category": "TECH",
                                  "price": 79.00,
                                  "stock": 10
                                }"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.title").value("设计模式"));
    }

    @Test
    @DisplayName("ISBN 重复返回 409，不是 400 也不是 500")
    void duplicateIsbnReturns409() throws Exception {
        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "重复的书",
                                  "author": "某人",
                                  "isbn": "%s",
                                  "category": "TECH",
                                  "price": 10.00
                                }""".formatted(EXISTING_ISBN)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(20409));
    }

    @Test
    @DisplayName("参数校验失败返回 400，提示具体是哪个字段")
    void validationFailure() throws Exception {
        mockMvc.perform(post("/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "",
                                  "author": "某人",
                                  "isbn": "9787111075753",
                                  "category": "TECH",
                                  "price": 10.00
                                }"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("书名不能为空"));
    }

    @Test
    @DisplayName("删除后查询返回 404")
    void deleteThenGone() throws Exception {
        String id = createBook();

        mockMvc.perform(delete("/books/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/books/" + id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(20404));
    }

    @Test
    @DisplayName("分类接口返回 5 个枚举值，前端下拉框直接用它")
    void categoriesEndpoint() throws Exception {
        mockMvc.perform(get("/books/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));
    }
}
