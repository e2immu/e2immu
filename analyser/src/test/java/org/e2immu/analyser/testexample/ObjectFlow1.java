package org.e2immu.analyser.testexample;

import org.e2immu.annotation.E2Container;

public class ObjectFlow1 {

    @E2Container
    static class KeyValue {
        public final String key;
        public final Integer value;

        public KeyValue(String key, Integer value) {
            this.key = key;
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }

        public String getKey() {
            return key;
        }
    }

    static Integer useKv(int k) {
        KeyValue keyValue = new KeyValue("key", k);
        return keyValue.value;
    }
}
