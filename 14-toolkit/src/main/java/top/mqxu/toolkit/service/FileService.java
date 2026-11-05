package top.mqxu.toolkit.service;

import cn.hutool.core.img.Img;
import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import top.mqxu.toolkit.common.BusinessException;
import top.mqxu.toolkit.common.ErrorCode;
import top.mqxu.toolkit.config.ToolkitProperties;
import top.mqxu.toolkit.dto.FileCheckResult;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * 文件上传安全
 *
 * @author mqxu
 * @date 2026-10-21
 * @description 按文件头识别真实类型，挡住改扩展名的做法；图片压缩与多文件打包
 */
@Service
@RequiredArgsConstructor
public class FileService {

    private static final Set<String> ALLOWED =
            Set.of("jpg", "jpeg", "png", "gif", "pdf", "zip", "docx", "xlsx");

    private final ToolkitProperties properties;

    public FileCheckResult check(MultipartFile file) {
        byte[] bytes = readBytes(file);
        String originalName = StrUtil.nullToEmpty(file.getOriginalFilename());
        String extension = StrUtil.nullToEmpty(FileUtil.extName(originalName)).toLowerCase();
        String realType = StrUtil.nullToEmpty(FileTypeUtil.getType(new ByteArrayInputStream(bytes)));

        // 白名单只看真实类型还不够，扩展名和文件头对不上的一律拒绝
        boolean allowed = ALLOWED.contains(realType.toLowerCase()) && sameType(extension, realType);

        String remark;
        if (StrUtil.isBlank(realType)) {
            remark = "扩展名是 " + extension + "，无法识别文件类型";
        } else if (sameType(extension, realType)) {
            remark = "扩展名与文件头一致";
        } else {
            remark = "扩展名是 " + extension + "，文件头实际是 " + realType;
        }

        return new FileCheckResult(originalName, extension, realType,
                bytes.length, DigestUtil.md5Hex(bytes), allowed, remark);
    }

    /**
     * 按最大宽度等比缩小，宽度没超就原样返回
     */
    public byte[] compress(MultipartFile file, int maxWidth) {
        if (maxWidth <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "maxWidth 必须大于 0");
        }
        try {
            Img img = Img.from(file.getInputStream());
            int width = img.getImg().getWidth(null);
            int height = img.getImg().getHeight(null);
            if (width > maxWidth) {
                int targetHeight = (int) Math.round((double) height * maxWidth / width);
                img.scale(maxWidth, targetHeight);
            }
            return ImgUtil.toBytes(img.getImg(), "png");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片处理失败：" + e.getMessage());
        }
    }

    /**
     * 多个文件打成一个 zip
     */
    public byte[] zip(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new BusinessException(ErrorCode.FILE_EMPTY, "请至少上传一个文件");
        }
        File dir = FileUtil.mkdir(new File(properties.getWorkDir(),
                "zip/" + IdUtil.fastSimpleUUID()));
        try {
            for (MultipartFile file : files) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                File dest = new File(dir, safeName(file.getOriginalFilename()));
                file.transferTo(dest);
            }
            File[] list = dir.listFiles();
            if (list == null || list.length == 0) {
                throw new BusinessException(ErrorCode.FILE_EMPTY);
            }
            File zip = new File(dir.getParentFile(), dir.getName() + ".zip");
            ZipUtil.zip(zip, false, list);
            return FileUtil.readBytes(zip);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "打包失败：" + e.getMessage());
        } finally {
            FileUtil.del(dir);
            FileUtil.del(new File(dir.getParentFile(), dir.getName() + ".zip"));
        }
    }

    private byte[] readBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        try {
            return file.getBytes();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件读取失败");
        }
    }

    private boolean sameType(String extension, String realType) {
        if (extension.equals(realType)) {
            return true;
        }
        // jpg 与 jpeg 是同一个格式
        return List.of("jpg", "jpeg").contains(extension)
                && List.of("jpg", "jpeg").contains(realType);
    }

    private String safeName(String fileName) {
        // FileUtil.getName 会把 / 和 \ 两种分隔符都处理掉，比手写 subAfter 稳妥
        String name = FileUtil.getName(StrUtil.nullToEmpty(fileName));
        if (StrUtil.isBlank(name) || name.contains("..")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "文件名不合法");
        }
        return name;
    }
}
