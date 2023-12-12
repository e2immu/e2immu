package org.e2immu.analyser.analyser.util;

import org.e2immu.graph.util.TimedLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

public class GraphCacheImpl implements Cache {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphCacheImpl.class);

    private final LinkedHashMap<Hash, CacheElement> cache;
    private final MessageDigest md;
    private int hits;
    private int misses;
    private int removals;
    private int sumSavings;

    private final TimedLogger timedLogger = new TimedLogger(LOGGER, 1000L);

    public GraphCacheImpl(int maxSize) {
        try {
            md = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        // LRU-cache
        cache = new LinkedHashMap<>(1000, 0.8f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Hash, CacheElement> eldest) {
                boolean remove = size() > maxSize;
                if (remove) {
                    removals++;
                    sumSavings += eldest.getValue().savings();
                }
                return remove;
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
            timedLogger.info("Graph cache {} hits, {} misses, {} removals, {} average savings", hits, misses,
                    removals, removals == 0 ? 0 : (double) sumSavings / removals);
            CacheElement inCache = cache.get(hash);
            if (inCache != null) {
                hits++;
                return inCache;
            }
            misses++;
            CacheElement newElement = elementSupplier.apply(hash);
            cache.put(hash, newElement);
            return newElement;
        }
    }
}
