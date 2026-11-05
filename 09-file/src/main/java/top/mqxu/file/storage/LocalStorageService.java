package top.mqxu.file.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.file.common.BusinessException;
import top.mqxu.file.common.ErrorCode;
import top.mqxu.file.config.FileProperties;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/**
 * 本地磁盘存储
 *
 * @author mqxu
 * @date 2026-11-04
 * @description 按日期分目录落盘，所有路径都先 normalize 再确认落在根目录内，挡住 ../ 穿越
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "file", name = "storage-type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {

    private final FileProperties properties;

    @Override
    public String type() {
        return "local";
    }

    @Override
    public void write(String objectKey, MultipartFile file) {
        Path target = resolve(objectKey);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
            log.info("文件写入本地：{}（{} 字节）", target, file.getSize());
        } catch (IOException e) {
            log.error("写入本地文件失败：{}", target, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "写入磁盘失败");
        }
    }

    @Override
    public StoredObject read(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
        }
        try {
            String contentType = Files.probeContentType(target);
            if (contentType == null) {
                contentType = URLConnection.guessContentTypeFromName(target.getFileName().toString());
            }
            return new StoredObject(objectKey, Files.size(target),
                    contentType == null ? StoredObject.DEFAULT_CONTENT_TYPE : contentType,
                    Files.newInputStream(target));
        } catch (IOException e) {
            log.error("读取本地文件失败：{}", target, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "读取磁盘失败");
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return Files.isRegularFile(resolve(objectKey));
    }

    @Override
    public void delete(String objectKey) {
        Path target = resolve(objectKey);
        try {
            if (!Files.deleteIfExists(target)) {
                throw new BusinessException(ErrorCode.FILE_NOT_FOUND);
            }
            // 顺手清掉空目录，日期目录不会越堆越多
            removeEmptyParents(target.getParent());
            log.info("删除本地文件：{}", target);
        } catch (IOException e) {
            log.error("删除本地文件失败：{}", target, e);
            throw new BusinessException(ErrorCode.STORAGE_ERROR, "删除磁盘文件失败");
        }
    }

    @Override
    public String url(String objectKey) {
        String prefix = properties.getLocal().getUrlPrefix();
        if (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix + "/" + objectKey;
    }

    /**
     * key 转磁盘路径。客户端可以随便传 key，必须确认结果仍落在根目录内
     */
    private Path resolve(String objectKey) {
        Path base = Paths.get(properties.getLocal().getBaseDir()).toAbsolutePath().normalize();
        Path target = base.resolve(objectKey).normalize();
        if (!target.startsWith(base) || target.equals(base)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法的文件路径");
        }
        return target;
    }

    private void removeEmptyParents(Path dir) {
        Path base = Paths.get(properties.getLocal().getBaseDir()).toAbsolutePath().normalize();
        Path current = dir;
        try {
            while (current != null && current.startsWith(base) && !current.equals(base)) {
                // 文件已被删掉，它所在的目录可能已经不在了，直接往上走
                if (Files.isDirectory(current)) {
                    try (Stream<Path> children = Files.list(current)) {
                        if (children.findAny().isPresent()) {
                            return;
                        }
                    }
                    Files.deleteIfExists(current);
                }
                current = current.getParent();
            }
        } catch (IOException e) {
            // 清理目录失败不影响删除结果，记一笔就行
            log.debug("清理空目录失败：{}", dir, e);
        }
    }
}
