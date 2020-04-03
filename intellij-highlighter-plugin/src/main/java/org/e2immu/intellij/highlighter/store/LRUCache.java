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
public class LRUCache<K, V> implements Cache<K, V> {
    private final Map<K, Long> recency = new HashMap<>();
    private final TreeMap<Long, K> dates = new TreeMap<>();
    private final Map<K, V> map = new HashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final HashMap<K, Long> created;

    private final int maxSize;
    private final long timeToLive;
    private final BiConsumer<K, V> uponEviction;

    private volatile long hits;
    private volatile long misses;

    public LRUCache(int maxSize) {
        this(maxSize, 0, null);
    }

    public LRUCache(int maxSize, BiConsumer<K, V> uponEviction) {
        this(maxSize, 0, uponEviction);
    }

    /**
     * @param maxSize
     * @param timeToLive   If an element is in the cache, and if it has been stored there
     *                     longer than this amount of milliseconds, it will be ignored in
     *                     a get and "null" will be returned, and the element will be
     *                     removed. There is no sweep; removal occurs at put time (LRU)
     *                     or at get time (time to live). Has nothing to do with the LRU
     *                     recencies.
     * @param uponEviction
     */
    public LRUCache(int maxSize, long timeToLive, BiConsumer<K, V> uponEviction) {
        this.maxSize = maxSize;
        this.uponEviction = uponEviction;
        this.timeToLive = timeToLive;
        if (timeToLive > 0) {
            created = new HashMap<>();
        } else {
            created = null;
        }
    }

    @Override
    public int size() {
        lock.lock();
        try {
            return map.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V get(K k) {
        lock.lock();
        try {
            final V v = map.get(k);
            if (v != null) {
                hits++;
                if (timeToLive > 0) {
                    final long creationTime = created.get(k);
                    if (new Date().getTime() - creationTime > timeToLive) {
                        // the object is too old. we cannot delete it, but we will not update its recency
                        removeInsideLock(k);
                        return null;
                    }
                }
                final long now = nextTime();
                final long oldTime = recency.put(k, now);
                dates.remove(oldTime);
                dates.put(now, k);
            } else {
                misses++;
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

    @Override
    public V put(K k, V v) {
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
                if (timeToLive > 0) {
                    created.put(k, now);
                }
            }
            if (map.size() == maxSize) {

                // remove least element
                final Entry<Long, K> oldestEntry = dates.firstEntry();
                final long removeDate = oldestEntry.getKey();
                final K removeKey = oldestEntry.getValue();
                recency.remove(removeKey);
                if (timeToLive > 0) {
                    created.remove(removeKey);
                }
                dates.remove(removeDate);
                final V removeValue = map.remove(removeKey);
                if (uponEviction != null) {
                    uponEviction.accept(removeKey, removeValue);
                }
            }
            return inMap;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public V remove(K k) {
        lock.lock();
        try {
            return removeInsideLock(k);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param remove - true = remove value
     */
    public void remove(Function<Entry<K, V>, Boolean> remove) {
        lock.lock();
        try {
            List<K> toRemove = new LinkedList<>();
            for (Entry<K, V> e : map.entrySet()) {
                if (remove.apply(e)) {
                    toRemove.add(e.getKey());
                }
            }
            for (K key : toRemove) {
                removeInsideLock(key);
            }
        } finally {
            lock.unlock();
        }
    }

    private V removeInsideLock(K k) {
        final V inMap = map.remove(k);
        if (inMap != null) {
            final long date = recency.remove(k);
            dates.remove(date);
            if (timeToLive > 0 && created != null) {
                created.remove(k);
            }
            if (uponEviction != null) {
                uponEviction.accept(k, inMap);
            }
        }
        return inMap;
    }

    @Override
    public void applyInReadLock(BiConsumer<K, V> f) {
        lock.lock();
        try {
            for (Map.Entry<K, V> e : map.entrySet()) {
                f.accept(e.getKey(), e.getValue());
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void clear() {
        lock.lock();
        try {
            map.clear();
            recency.clear();
            dates.clear();
            if (timeToLive > 0 && created != null) {
                created.clear();
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
