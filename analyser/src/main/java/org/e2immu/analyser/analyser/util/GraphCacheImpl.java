package org.e2immu.analyser.analyser.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class GraphCacheImpl implements Cache {
    private final LinkedHashMap<Hash, CacheElement> cache;
    private final MessageDigest md;

    public GraphCacheImpl(int maxSize) {
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        // LRU-cache
        cache = new LinkedHashMap<>(maxSize, 0.8f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Hash, CacheElement> eldest) {
                return size() > maxSize;
            }
        };
    }

    public Hash createHash(String string) {
        synchronized (md) {
            md.reset();
            md.update(string.getBytes());
            byte[] digest = md.digest();
            return new Hash(digest);
        }
    }

    @Override
    public CacheElement computeIfAbsent(Hash hash, Function<Hash, CacheElement> elementSupplier) {
        synchronized (cache) {
            return cache.computeIfAbsent(hash, elementSupplier);
        }
    }
}
