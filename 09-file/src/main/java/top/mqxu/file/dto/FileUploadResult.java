package top.mqxu.file.dto;

/**
 * 上传结果
 *
 * @param objectKey    对象 key，删除、下载都靠它
 * @param originalName 原始文件名
 * @param url          访问地址
 * @param size         字节数
 * @param contentType  内容类型
 * @param storage      实际存储方式，local / oss
 * @author mqxu
 * @date 2026-11-04
 */
public record FileUploadResult(String objectKey, String originalName, String url,
                               long size, String contentType, String storage) {
}
