package cn.edu.academic.support.storage;

import java.io.IOException;
import java.io.InputStream;

/** Keys are opaque private identifiers, never client-supplied filesystem paths. */
public interface ObjectStorage {
    void put(String key, InputStream input, long size, String mimeType) throws IOException;
    InputStream open(String key) throws IOException;
    long size(String key) throws IOException;
    void delete(String key);
    default String signedPut(String key, long size, String mime) { return null; }
    default String signedGet(String key) { return null; }
}
