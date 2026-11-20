package com.aps.domain.storage;

import java.io.IOException;
import java.io.InputStream;

/** 文件存储抽象（技术方案 §1.9）：P0 本地 FS 实现，未来可换云 OSS 不改上层。 */
public interface ObjectStorage {

    /** 保存对象并返回 key。 */
    String put(String key, InputStream content) throws IOException;

    InputStream get(String key) throws IOException;

    void delete(String key) throws IOException;

    boolean exists(String key);
}
