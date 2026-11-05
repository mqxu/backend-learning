package top.mqxu.toolkit.dto;

/**
 * 合并结果
 *
 * @param fileId      文件标识
 * @param fileName    文件名
 * @param size        文件大小（字节）
 * @param md5         合并后计算的 MD5
 * @param downloadUrl 下载地址
 * @author mqxu
 * @date 2026-10-21
 */
public record ChunkMergeResponse(String fileId, String fileName, long size,
                                 String md5, String downloadUrl) {
}
