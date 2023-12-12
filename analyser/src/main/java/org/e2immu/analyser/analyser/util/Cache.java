package org.e2immu.analyser.analyser.util;

import java.util.Arrays;
import java.util.function.Function;

public interface Cache {

    interface CacheElement {
    }

    record Hash(byte[] bytes) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            return obj instanceof Hash o && Arrays.equals(bytes, o.bytes);
        }

        @Override
        public int hashCode() {
            return bytes[0] + (bytes[1] << 8) + (bytes[2] << 16) + (bytes[3] << 24);
        }
    }

    Hash createHash(String string);

    CacheElement computeIfAbsent(Hash hash, Function<Hash, CacheElement> elementSupplier);
}
