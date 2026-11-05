package top.mqxu.quickstart;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 查询存在的用户返回200() throws Exception {
        mockMvc.perform(get("/user/1002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("李四"));
    }

    @Test
    void 查询不存在的用户返回404() throws Exception {
        mockMvc.perform(get("/user/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void 按关键词过滤只返回匹配项() throws Exception {
        mockMvc.perform(get("/user/list").param("keyword", "张"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("张三"));
    }
}
