/*
 * Copyright (c) 2007-2018, MDCPartners cvba, Belgium.
 * Unauthorized copying of this file, via any medium, is strictly prohibited.
 * Proprietary and confidential.
 */
package org.e2immu.intellij.highlighter.store;

import org.e2immu.annotation.Container;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;

@Container
public class LRUCache<K, V> {
    private final Map<K, Long> recency = new HashMap<>();
    private final TreeMap<Long, K> dates = new TreeMap<>();
    private final Map<K, V> map = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final HashMap<K, Long> created;

    private final int maxSize;
    private final long timeToLive;

    private volatile long hits;
    private volatile long misses;

    /**
     * @param maxSize      Maximum size of the cache
     * @param timeToLive   If an element is in the cache, and if it has been stored there
     *                     longer than this amount of milliseconds, it will be ignored in
     *                     a get and "null" will be returned, and the element will be
     *                     removed. There is no sweep; removal occurs at put time (LRU)
     *                     or at get time (time to live). Has nothing to do with the LRU
     *                     recencies.
     */
    public LRUCache(int maxSize, long timeToLive) {
        this.maxSize = maxSize;
        this.timeToLive = timeToLive;
        if (timeToLive > 0) {
            created = new HashMap<>();
        } else {
            created = null;
        }
    }

    public void clear() {
        lock.lock();
        try {
            recency.clear();
            dates.clear();
            map.clear();
            if(created != null) created.clear();
        } finally {
            lock.unlock();
        }
    }

    public V get(K k) {
        lock.lock();
        try {
            final V v = map.get(k);
            if (v != null) {
                long h = hits;
                hits = h + 1;
                if (created != null) {
                    final long creationTime = created.get(k);
                    if (new Date().getTime() - creationTime > timeToLive) {
                        // the object is too old. we cannot delete it, but we will not update its recency
                        return null;
                    }
                }
                final long now = nextTime();
                final Long oldTime = recency.put(k, now);
                if (oldTime != null) {
                    dates.remove(oldTime);
                }
                dates.put(now, k);
            } else {
                long m = misses;
                misses = m + 1;
            }
            return v;
        } finally {
            lock.unlock();
        }
    }

    private long nextTime() {
        long now = new Date().getTime();
        if (dates.size() > 0 && dates.lastKey() >= now) {
            now = dates.lastKey() + 1;
        }
        return now;
    }

    public void put(K k, V v) {
        if (v == null) {
            throw new NullPointerException("Null not accepted as value for the LRU Cache; key is " + k);
        }
        lock.lock();
        try {
            final V inMap = map.put(k, v);
            if (inMap == null) {
                //new in the cache!
                final long now = nextTime();
                recency.put(k, now);
                dates.put(now, k);
                if (created != null) {
                    created.put(k, now);
                }
            }
            if (map.size() == maxSize) {
                // map is full, we remove the oldest entry
                final Entry<Long, K> oldestEntry = dates.firstEntry();
                final long removeDate = oldestEntry.getKey();
                final K removeKey = oldestEntry.getValue();
                recency.remove(removeKey);
                if (created != null) {
                    created.remove(removeKey);
                }
                dates.remove(removeDate);
                map.remove(removeKey);
            }
        } finally {
            lock.unlock();
        }
    }

    public long getHits() {
        return hits;
    }

    public long getMisses() {
        return misses;
    }
}
