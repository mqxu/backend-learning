package top.mqxu.toolkit.dto;

/**
 * 初始化分片上传
 *
 * @param fileName    文件名
 * @param totalChunks 分片总数
 * @param md5         整个文件的 MD5，可选，传了会在合并时校验
 * @author mqxu
 * @date 2026-10-21
 */
public record ChunkInitRequest(String fileName, Integer totalChunks, String md5) {
}
