package top.mqxu.toolkit.support;

import lombok.Getter;

/**
 * 分片上传会话
 *
 * @author mqxu
 * @date 2026-10-21
 */
@Getter
public class ChunkSession {

    private final String uploadId;
    private final String fileName;
    private final int totalChunks;
    private final long chunkSize;
    private final String md5;
    private final long createdAt = System.currentTimeMillis();

    public ChunkSession(String uploadId, String fileName, int totalChunks,
                        long chunkSize, String md5) {
        this.uploadId = uploadId;
        this.fileName = fileName;
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.md5 = md5;
    }
}
