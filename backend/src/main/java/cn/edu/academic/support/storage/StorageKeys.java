package cn.edu.academic.support.storage;

public final class StorageKeys {
    private StorageKeys() {}
    public static void validate(String key) {
        if (key==null || !key.matches("(?:photos|grades|moral|pending)/[a-zA-Z0-9_-]+\\.[a-zA-Z0-9]+"))
            throw new IllegalArgumentException("Invalid storage key");
    }
}
