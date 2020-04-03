package org.e2immu.kvstore;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class Project {
    private static final Logger LOGGER = LoggerFactory.getLogger(Project.class);

    static class Container {
        final String value;
        final Instant updated;
        volatile Instant read;

        public Container(String value, Instant previousRead) {
            this.value = value;
            this.read = previousRead;
            updated = LocalDateTime.now().toInstant(ZoneOffset.UTC);
        }
    }

    private final Map<String, Container> kvStore = new ConcurrentHashMap<>();
    public final String name;

    public Project(String name) {
        this.name = name;
    }

    public String set(String key, String value) {
        Container prev = kvStore.get(key);
        if (prev == null) {
            Container container = new Container(value, null);
            kvStore.put(key, container);
            LOGGER.debug("Initialized value of key " + key + " to " + value);
            return null;
        }
        if (!prev.value.equals(value)) {
            Container container = new Container(value, prev.read);
            kvStore.put(key, container);
            LOGGER.debug("Updated value of key " + key + " to " + value);
        } else {
            LOGGER.debug("Value of key " + key + " has stayed the same at " + value);
        }
        return prev.value;
    }

    public String get(String key) {
        Container container = kvStore.get(key);
        if (container == null) {
            LOGGER.debug("Key " + key + " has no value");
            return null;
        }
        container.read = LocalDateTime.now().toInstant(ZoneOffset.UTC);
        LOGGER.debug("Read key " + key + ": " + container.value);
        return container.value;
    }

    public String remove(String key) {
        Container container = kvStore.remove(key);
        LOGGER.debug("Removed key " + key);
        return container == null ? null : container.value;
    }

    public Map<String, String> recentlyReadAndUpdatedAfterwards(Set<String> queried, long readWithinMillis) {
        Map<String, String> result = new HashMap<>();
        Instant now = LocalDateTime.now().toInstant(ZoneOffset.UTC);
        for (Map.Entry<String, Container> entry : kvStore.entrySet()) {
            String key = entry.getKey();
            if (!queried.contains(key)) {
                Container container = entry.getValue();
                if (container.read != null && container.read.plusMillis(readWithinMillis).isAfter(now)
                        && container.read.isBefore(container.updated)) {
                    result.put(key, container.value);
                }
            }
        }
        LOGGER.debug("Added " + result.size() + " kv entries which were recently read before having been updated");
        return result;
    }

    public void visit(BiConsumer<String, String> visitor) {
        for (Map.Entry<String, Container> entry : kvStore.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue().value;
            visitor.accept(key, value);
        }
    }
}
