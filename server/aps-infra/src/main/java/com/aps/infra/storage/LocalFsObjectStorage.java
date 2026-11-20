package com.aps.infra.storage;

import com.aps.domain.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/** 本地 FS 实现（技术方案 §1.9）：挂载目录持久化，无 MinIO。 */
@Component
public class LocalFsObjectStorage implements ObjectStorage {

    private final Path root;

    public LocalFsObjectStorage(@Value("${storage.local-dir:./data/files}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("cannot create local storage directory", e);
        }
    }

    /** 防目录穿越：解析后必须仍在 root 之下。 */
    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("invalid object key");
        }
        return target;
    }

    @Override
    public String put(String key, InputStream content) throws IOException {
        Path target = resolve(key);
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        return key;
    }

    @Override
    public InputStream get(String key) throws IOException {
        return Files.newInputStream(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }
}
