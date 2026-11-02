package cn.edu.academic.support.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.file.*;

@Component
@ConditionalOnProperty(name="app.storage.provider", havingValue="local", matchIfMissing=true)
public class LocalObjectStorage implements ObjectStorage {
    private final Path root;
    public LocalObjectStorage(@Value("${app.storage.private-root}") String root) throws IOException {
        this.root=Path.of(root).toAbsolutePath().normalize(); Files.createDirectories(this.root);
    }
    private Path path(String key) {
        StorageKeys.validate(key);
        Path value=root.resolve(key).normalize();
        if (!value.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return value;
    }
    public void put(String key, InputStream in, long size, String mime) throws IOException {
        Path target=path(key); Files.createDirectories(target.getParent());
        try (var out=Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) { in.transferTo(out); }
    }
    public InputStream open(String key) throws IOException { return Files.newInputStream(path(key)); }
    public long size(String key) throws IOException { return Files.size(path(key)); }
    public void delete(String key) { try { Files.deleteIfExists(path(key)); } catch(IOException e) { throw new IllegalStateException("File cleanup failed"); } }
}
