package com.campus.activity.file.support;

import com.campus.activity.common.BizException;
import com.campus.activity.common.ResultCode;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

/**
 * 文件头识别。
 * 只看前 12 个字节的魔数：请求里的 Content-Type 和文件扩展名都能伪造，文件头不能。
 */
public final class FileTypeDetector {

    private static final int HEAD_LENGTH = 12;

    private static final Set<String> IMAGE_TYPES = Set.of("jpeg", "png", "gif", "bmp", "webp");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");
    private static final Set<String> EXECUTABLE_TYPES = Set.of("exe", "elf");

    private FileTypeDetector() {
    }

    /** 识别文件真实类型，识别不出来返回 unknown */
    public static String detect(MultipartFile file) {
        byte[] head = new byte[HEAD_LENGTH];
        int length;
        try (InputStream in = file.getInputStream()) {
            length = in.readNBytes(head, 0, HEAD_LENGTH);
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_STORAGE_ERROR, "读取文件头失败");
        }
        return detect(head, length);
    }

    private static String detect(byte[] h, int len) {
        if (len < 4) {
            return "unknown";
        }
        int b0 = h[0] & 0xFF;
        int b1 = h[1] & 0xFF;
        int b2 = h[2] & 0xFF;
        int b3 = h[3] & 0xFF;

        if (b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) {
            return "jpeg";
        }
        if (b0 == 0x89 && b1 == 'P' && b2 == 'N' && b3 == 'G') {
            return "png";
        }
        if (b0 == 'G' && b1 == 'I' && b2 == 'F' && b3 == '8') {
            return "gif";
        }
        if (b0 == 'B' && b1 == 'M') {
            return "bmp";
        }
        if (b0 == 'R' && b1 == 'I' && b2 == 'F' && b3 == 'F'
                && len >= 12 && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') {
            return "webp";
        }
        if (b0 == '%' && b1 == 'P' && b2 == 'D' && b3 == 'F') {
            return "pdf";
        }
        if (b0 == 'P' && b1 == 'K' && (b2 == 3 || b2 == 5)) {
            // docx / xlsx / pptx 本质都是 zip 容器
            return "zip";
        }
        if (b0 == 'R' && b1 == 'a' && b2 == 'r' && b3 == '!') {
            return "rar";
        }
        if (b0 == '7' && b1 == 'z' && b2 == 0xBC && b3 == 0xAF) {
            return "7z";
        }
        if (b0 == 0x1F && b1 == 0x8B) {
            return "gzip";
        }
        if (b0 == 'M' && b1 == 'Z') {
            return "exe";
        }
        if (b0 == 0x7F && b1 == 'E' && b2 == 'L' && b3 == 'F') {
            return "elf";
        }
        return "unknown";
    }

    public static boolean isImageExtension(String extension) {
        return IMAGE_EXTENSIONS.contains(extension);
    }

    public static boolean isImageType(String type) {
        return IMAGE_TYPES.contains(type);
    }

    public static boolean isExecutable(String type) {
        return EXECUTABLE_TYPES.contains(type);
    }
}
