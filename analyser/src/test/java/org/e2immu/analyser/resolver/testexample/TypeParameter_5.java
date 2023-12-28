package org.e2immu.analyser.resolver.testexample;

import java.util.HashMap;

public class TypeParameter_5 {

    interface Base {
        long getId();
    }

    static class MyHashMap<T> extends HashMap<Long, T> {
        @Override
        public T put(Long key, T value) {
            assert key != null;
            assert value != null;
            return super.put(key, value);
        }
    }

    static <T extends Base> MyHashMap<T> mapDataByID(T[] data) {
        MyHashMap<T> result = new MyHashMap<>();
        for (int i = 0; i < data.length; ++i) {
            result.put(data[i].getId(), data[i]);
        }
        return result;
    }

    <T extends Base> void method1(T[] data) {
        MyHashMap objById = mapDataByID(data);
    }
}

