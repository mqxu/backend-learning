package com.campus.activity.file.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * 读出来的文件。
 *
 * @param objectKey   对象 key
 * @param size        字节数
 * @param contentType 内容类型，取不到时为 application/octet-stream
 * @param stream      文件流，用完关闭
 */
public record StoredObject(String objectKey, long size, String contentType, InputStream stream)
        implements Closeable {

    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
