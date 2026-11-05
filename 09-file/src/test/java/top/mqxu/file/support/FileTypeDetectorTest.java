package top.mqxu.file.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件头识别的单元测试。detect(byte[], int) 是包级可见的，测试类放在同一个包下就能直接调。
 *
 * @author mqxu
 */
class FileTypeDetectorTest {

    /** 取字节数组的前 n 个字节喂给识别器 */
    private String detect(byte[] head) {
        return FileTypeDetector.detect(head, head.length);
    }

    private byte[] bytes(int... values) {
        byte[] array = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            array[i] = (byte) values[i];
        }
        return array;
    }

    @Test
    @DisplayName("常见图片格式按魔数识别")
    void detectImages() {
        assertThat(detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0, 0, 0, 0, 0, 0, 0))).isEqualTo("jpeg");
        assertThat(detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0))).isEqualTo("png");
        assertThat(detect(bytes('G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0))).isEqualTo("gif");
        assertThat(detect(bytes('B', 'M', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))).isEqualTo("bmp");
    }

    @Test
    @DisplayName("webp 要看第 8 到 11 字节的 WEBP 标记")
    void detectWebp() {
        assertThat(detect(bytes('R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P')))
                .isEqualTo("webp");
        // 只有 RIFF 没有 WEBP，是别的 RIFF 容器，不算 webp
        assertThat(detect(bytes('R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'A', 'V', 'I', ' ')))
                .isEqualTo("unknown");
    }

    @Test
    @DisplayName("PDF 与 Office 文档")
    void detectDocuments() {
        assertThat(detect(bytes('%', 'P', 'D', 'F', '-', '1', '.', '7', 0, 0, 0, 0))).isEqualTo("pdf");
        // docx / xlsx / pptx 本质都是 zip
        assertThat(detect(bytes('P', 'K', 3, 4, 0, 0, 0, 0, 0, 0, 0, 0))).isEqualTo("zip");
    }

    @Test
    @DisplayName("可执行文件能被认出来")
    void detectExecutables() {
        assertThat(detect(bytes('M', 'Z', 0x90, 0x00, 0, 0, 0, 0, 0, 0, 0, 0))).isEqualTo("exe");
        assertThat(FileTypeDetector.isExecutable("exe")).isTrue();

        assertThat(detect(bytes(0x7F, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0, 0, 0))).isEqualTo("elf");
        assertThat(FileTypeDetector.isExecutable("elf")).isTrue();
    }

    @Test
    @DisplayName("纯文本识别不出来，给 unknown")
    void textIsUnknown() {
        byte[] text = "这是一段普通文本".getBytes(StandardCharsets.UTF_8);
        assertThat(detect(text)).isEqualTo("unknown");
        assertThat(FileTypeDetector.isExecutable("unknown")).isFalse();
    }

    @Test
    @DisplayName("不足 4 个字节直接算 unknown，不越界读")
    void tooShortIsUnknown() {
        assertThat(FileTypeDetector.detect(new byte[]{(byte) 0x89, 'P'}, 2)).isEqualTo("unknown");
        assertThat(FileTypeDetector.detect(new byte[0], 0)).isEqualTo("unknown");
    }

    @Test
    @DisplayName("图片扩展名与图片类型的判断口径一致")
    void imageChecks() {
        assertThat(FileTypeDetector.isImageExtension("jpg")).isTrue();
        assertThat(FileTypeDetector.isImageExtension("webp")).isTrue();
        assertThat(FileTypeDetector.isImageExtension("pdf")).isFalse();

        assertThat(FileTypeDetector.isImageType("png")).isTrue();
        assertThat(FileTypeDetector.isImageType("zip")).isFalse();
    }
}
