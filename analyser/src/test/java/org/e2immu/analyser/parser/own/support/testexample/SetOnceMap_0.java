package org.e2immu.analyser.parser.own.support.testexample;

import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.Freezable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@ImmutableContainer(after = "frozen", hc = true)
public class SetOnceMap_0<K, V> extends Freezable {

    private final Map<K, V> map = new HashMap<>();

    /*
    Return entries that cannot be set, so that the stream is immutable.
     */
    private static class Entry<K, V> implements Map.Entry<K, V> {

        private final K k;
        private final V v;

        private Entry(K k, V v) {
            this.k = Objects.requireNonNull(k);
            this.v = Objects.requireNonNull(v);
        }

        @Override
        public K getKey() {
            return k;
        }

        @Override
        public V getValue() {
            return v;
        }

        @Override
        public V setValue(V value) {
            Objects.requireNonNull(value);
            throw new UnsupportedOperationException();
        }

        @Override
        public String toString() {
            return k + "=" + v;
        }
    }

    @NotNull(content = true, contract = true)
    @NotModified
    @Independent(hc = true)
    public Stream<Map.Entry<K, V>> stream() {
        return map.entrySet().stream().map(e -> new Entry<>(e.getKey(), e.getValue()));
    }
}
