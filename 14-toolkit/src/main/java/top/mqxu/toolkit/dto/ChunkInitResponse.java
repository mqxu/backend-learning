package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 初始化结果
 *
 * @param uploadId    上传会话标识
 * @param chunkSize   建议分片大小（字节）
 * @param totalChunks 分片总数
 * @param uploaded    已上传的分片序号，用于断点续传
 * @author mqxu
 * @date 2026-10-21
 */
public record ChunkInitResponse(String uploadId, long chunkSize,
                                int totalChunks, List<Integer> uploaded) {
}
