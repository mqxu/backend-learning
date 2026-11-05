package top.mqxu.toolkit.dto;

import java.util.List;

/**
 * 分片状态
 *
 * @param uploadId    上传会话标识
 * @param totalChunks 分片总数
 * @param uploaded    已上传
 * @param missing     缺失
 * @author mqxu
 * @date 2026-10-21
 */
public record ChunkStatusResponse(String uploadId, int totalChunks,
                                  List<Integer> uploaded, List<Integer> missing) {
}
