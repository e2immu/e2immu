package org.e2immu.analyser.parser.independence.testexample;

import org.e2immu.annotation.Independent;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

public class Independent1_12<K, V> {

    private final Map<K, V> map = new HashMap<>();

    public void put(K k, V v) {
        this.map.put(k, v);
    }

    @Independent(hc = true)
    public Stream<Map.Entry<K, V>> stream() {
        Set<Map.Entry<K, V>> entries = map.entrySet();
        Stream<Map.Entry<K, V>> stream = entries.stream();
        return stream.map(e -> new Entry<>(e.getKey(), e.getValue()));
    }

    @Independent(hc = true)
    public Stream<Map.Entry<K, V>> stream2() {
        Set<Map.Entry<K, V>> entries = map.entrySet();
        Stream<Map.Entry<K, V>> stream = entries.stream();
        Stream<Map.Entry<K, V>> mapped = stream.map(e -> new Entry<>(e.getKey(), e.getValue()));
        return mapped;
    }


    private record Entry<K, V>(K k, V v) implements Map.Entry<K, V> {
        @Override
        public K getKey() {
            return Objects.requireNonNull(k);
        }

        @Override
        public V getValue() {
            return Objects.requireNonNull(v);
        }

        @Override
        public V setValue(V value) {
            Objects.requireNonNull(value);
            throw new UnsupportedOperationException();
        }
    }
}
