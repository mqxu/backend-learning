package top.mqxu.file;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import top.mqxu.file.config.FileProperties;
import top.mqxu.file.service.FileService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 上传、下载、删除的完整链路，以及五道校验。
 *
 * <p>文件落在 target/test-uploads，不污染仓库里的 uploads 目录；每个用例前清空。</p>
 *
 * @author mqxu
 */
@SpringBootTest(properties = "file.local.base-dir=target/test-uploads")
@AutoConfigureMockMvc
class FileApiTest {

    private static final byte[] TXT = "这是一份用于测试的文本文件。\n第二行。\n".getBytes(StandardCharsets.UTF_8);

    /** 1x1 的透明 PNG，文件头是真的 */
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8AAAwAB/gEBQ7X7AAAAAElFTkSuQmCC");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileProperties properties;

    @Autowired
    private FileService fileService;

    @BeforeEach
    void clean() throws IOException {
        Path base = Paths.get(properties.getLocal().getBaseDir()).toAbsolutePath().normalize();
        if (!Files.exists(base)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(base)) {
            walk.sorted(Comparator.reverseOrder())
                    .filter(p -> !p.equals(base))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {
                            // 清理失败不影响用例本身
                        }
                    });
        }
    }

    private String upload(String fieldName, String fileName, String contentType, byte[] content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(fieldName, fileName, contentType, content);
        String body = mockMvc.perform(multipart("/file/upload").file(file))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.objectKey");
    }

    @Test
    @DisplayName("上传文本：key 按 目录/年/月/日/UUID.扩展名 生成")
    void uploadText() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", TXT);

        mockMvc.perform(multipart("/file/upload").file(file).param("dir", "docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.storage").value("local"))
                .andExpect(jsonPath("$.data.originalName").value("note.txt"))
                .andExpect(jsonPath("$.data.objectKey").value(
                        org.hamcrest.Matchers.matchesPattern("docs/\\d{4}/\\d{2}/\\d{2}/[0-9a-f]{32}\\.txt")));
    }

    @Test
    @DisplayName("上传图片：真的 PNG 能过，伪装成 PNG 的文本过不了")
    void uploadImage() throws Exception {
        MockMultipartFile real = new MockMultipartFile("file", "dot.png", "image/png", PNG);
        mockMvc.perform(multipart("/file/upload/image").file(real))
                .andExpect(status().isOk());

        MockMultipartFile fake = new MockMultipartFile("file", "fake.png", "image/png",
                "这不是 PNG，只是一段文本".getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/file/upload/image").file(fake))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10402));
    }

    @Test
    @DisplayName("下载：带 attachment 头，内容与上传的一致")
    void downloadContent() throws Exception {
        String key = upload("file", "note.txt", "text/plain", TXT);

        byte[] downloaded = mockMvc.perform(get("/file/download/" + key))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(downloaded).isEqualTo(TXT);
    }

    @Test
    @DisplayName("预览：inline，浏览器直接打开")
    void previewInline() throws Exception {
        String key = upload("file", "note.txt", "text/plain", TXT);

        mockMvc.perform(get("/file/preview/" + key))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("inline")));
    }

    @Test
    @DisplayName("上传后能通过 /uploads 静态映射直接访问")
    void staticMapping() throws Exception {
        String key = upload("file", "note.txt", "text/plain", TXT);

        byte[] served = mockMvc.perform(get("/uploads/" + key))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(served).isEqualTo(TXT);
    }

    @Test
    @DisplayName("删除后下载返回 404，再删一次也是 404")
    void deleteThenGone() throws Exception {
        String key = upload("file", "note.txt", "text/plain", TXT);

        mockMvc.perform(delete("/file/" + key)).andExpect(status().isOk());

        mockMvc.perform(get("/file/download/" + key))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(10404));

        mockMvc.perform(delete("/file/" + key))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("空文件被拒，业务码 10401")
    void emptyFileRejected() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/file/upload").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10401));
    }

    @Test
    @DisplayName("扩展名不在白名单里被拒，业务码 10402")
    void extensionNotAllowed() throws Exception {
        MockMultipartFile sh = new MockMultipartFile("file", "run.sh", "text/plain",
                "echo hi".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/file/upload").file(sh))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10402));
    }

    @Test
    @DisplayName("改了扩展名的 exe 也过不了：文件头是 MZ")
    void disguisedExecutableRejected() throws Exception {
        byte[] exe = new byte[]{'M', 'Z', (byte) 0x90, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        MockMultipartFile fake = new MockMultipartFile("file", "photo.jpg", "image/jpeg", exe);

        mockMvc.perform(multipart("/file/upload").file(fake))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(10402));
    }

    @Test
    @DisplayName("目录名带 ../ 被拒，业务码 400")
    void illegalDirRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "note.txt", "text/plain", TXT);

        mockMvc.perform(multipart("/file/upload").file(file).param("dir", "../evil"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("下载时用编码后的 ../ 穿越，被拒")
    void pathTraversalRejected() throws Exception {
        // 点编码、斜杠保留：这样请求还能匹配到 /file/download/{*key}，
        // key 解出来是 ../../etc/passwd，交给 service 的 resolve 拦
        mockMvc.perform(get("/file/download/%2E%2E/%2E%2E/etc/passwd"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("批量上传中途失败：已经写进去的会被清掉，不留半截数据")
    void batchRollsBack() throws Exception {
        Path base = Paths.get(properties.getLocal().getBaseDir()).toAbsolutePath().normalize();
        long before = countFiles(base);

        MockMultipartFile good = new MockMultipartFile("files", "a.txt", "text/plain", TXT);
        MockMultipartFile bad = new MockMultipartFile("files", "b.exe", "application/octet-stream",
                new byte[]{'M', 'Z', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        mockMvc.perform(multipart("/file/upload/batch").file(good).file(bad))
                .andExpect(status().isBadRequest());

        assertThat(countFiles(base)).isEqualTo(before);
    }

    @Test
    @DisplayName("存储信息接口返回当前生效的配置")
    void storageInfo() throws Exception {
        mockMvc.perform(get("/file/storage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storage").value("local"))
                .andExpect(jsonPath("$.data.maxFileSize").value("10MB"))
                .andExpect(jsonPath("$.data.urlPrefix").value("/uploads"));
    }

    @Test
    @DisplayName("service 层直接读越界 key 抛 400，不是 500")
    void serviceRejectsTraversal() {
        assertThatThrownBy(() -> fileService.read("../../etc/passwd"))
                .isInstanceOf(top.mqxu.file.common.BusinessException.class)
                .hasMessageContaining("非法的文件路径");
    }

    private long countFiles(Path base) throws IOException {
        if (!Files.exists(base)) {
            return 0;
        }
        try (Stream<Path> walk = Files.walk(base)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }
}
