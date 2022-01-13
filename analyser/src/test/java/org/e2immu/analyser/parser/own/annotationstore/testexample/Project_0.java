/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.parser.own.annotationstore.testexample;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/*
literal copy of the Project class in e2immu/annotation-store, version 0.1.2
 */

public class Project_0 {

    private static final Logger LOGGER = LoggerFactory.getLogger(Project_0.class);

    static class Container {
        final String value;
        final Instant updated;
        private volatile Instant read;

        public Container(String value, Instant previousRead) {
            this.value = value;
            this.read = previousRead;
            updated = LocalDateTime.now().toInstant(ZoneOffset.UTC);
        }
    }

    private final Map<String, Container> kvStore = new ConcurrentHashMap<>();
    public final String name;

    public Project_0(String name) {
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

