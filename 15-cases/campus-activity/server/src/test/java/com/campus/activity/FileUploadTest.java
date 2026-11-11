package com.campus.activity;

import com.campus.activity.file.config.FileProperties;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 文件上传链路测试：登录拿 token → 上传 → 校验磁盘落盘与 /uploads 静态访问 → 非法输入被拒。
 * 上传目录用 target/test-uploads 覆盖，避免污染 server/uploads；每个用例前后清理，测试结束整体删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "campus.file.storage-type=local",
        "campus.file.local.base-dir=target/test-uploads"
})
class FileUploadTest {

    private static final Path TEST_UPLOAD_DIR = Paths.get("target/test-uploads").toAbsolutePath().normalize();

    /** 1x1 透明 PNG，文件头是真的 */
    private static final byte[] PNG_1X1 = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/gEBQ7X7AAAAAElFTkSuQmCC");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileProperties fileProperties;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        cleanUploadDir();
        // dev 模式登录，走 mock openid，不请求微信服务器
        String body = mockMvc.perform(post("/api/app/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"devOpenId\":\"mock-student-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(body, "$.data.token");
    }

    @AfterAll
    static void tearDown() throws IOException {
        cleanUploadDir();
    }

    @Test
    @DisplayName("上传真实 PNG：code=0、url 以 /uploads/ 开头、磁盘落盘且可直接 GET 访问")
    void uploadPngThenAccessByUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", PNG_1X1);

        String body = mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("bizType", "avatar")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.storage").value("local"))
                .andExpect(jsonPath("$.data.originalName").value("avatar.png"))
                .andExpect(jsonPath("$.data.size").value(PNG_1X1.length))
                .andExpect(jsonPath("$.data.url").value(startsWith("/uploads/")))
                .andExpect(jsonPath("$.data.objectKey").value(
                        matchesPattern("avatar/\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.png")))
                .andReturn().getResponse().getContentAsString();

        String objectKey = JsonPath.read(body, "$.data.objectKey");
        String url = JsonPath.read(body, "$.data.url");

        // 对象 key 必须真的落在配置的上传根目录下，且内容与原文件一致
        Path stored = Paths.get(fileProperties.getLocal().getBaseDir())
                .toAbsolutePath().normalize().resolve(objectKey);
        assertThat(stored).isRegularFile();
        assertThat(Files.readAllBytes(stored)).isEqualTo(PNG_1X1);

        // 返回的 url 不带 token 也能直接访问，这是 LocalResourceConfig 把 baseDir 映射到 urlPrefix 的效果
        byte[] served = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(served).isEqualTo(PNG_1X1);
    }

    @Test
    @DisplayName("伪装成 PNG 的文本文件被拒：业务码 70003")
    void rejectFakePng() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", "image/png",
                "这不是 PNG，只是一段普通文本".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/files/upload")
                        .file(fake)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(70003));
    }

    @Test
    @DisplayName("空文件被拒：业务码 70001")
    void rejectEmptyFile() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        mockMvc.perform(multipart("/api/files/upload")
                        .file(empty)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(70001));
    }

    @Test
    @DisplayName("非法 bizType 被拒：业务码 50001")
    void rejectIllegalBizType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", PNG_1X1);

        mockMvc.perform(multipart("/api/files/upload")
                        .file(file)
                        .param("bizType", "../evil")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(50001));
    }

    @Test
    @DisplayName("存储信息接口返回当前生效的 local 配置")
    void storageInfo() throws Exception {
        mockMvc.perform(get("/api/files/storage")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.storage").value("local"))
                .andExpect(jsonPath("$.data.baseDir").value("target/test-uploads"))
                .andExpect(jsonPath("$.data.urlPrefix").value("/uploads"));
    }

    /** 递归删除测试上传目录（含目录本身），测试之间互不影响，测试结束不残留文件 */
    private static void cleanUploadDir() throws IOException {
        if (!Files.exists(TEST_UPLOAD_DIR)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(TEST_UPLOAD_DIR)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ignored) {
                            // 删除失败不影响用例本身
                        }
                    });
        }
    }
}
