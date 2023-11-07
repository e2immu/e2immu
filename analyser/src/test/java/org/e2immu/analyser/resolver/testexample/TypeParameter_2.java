package org.e2immu.analyser.resolver.testexample;

import java.lang.reflect.Array;

public class TypeParameter_2 {

    static class WithId implements Cloneable {
        int id;

        @Override
        public Object clone() {
            try {
                return super.clone();
            } catch (CloneNotSupportedException ignore) {
                throw new RuntimeException();
            }
        }
    }

    public static <T extends WithId> T[] method(T[] withIds) {
        T[] result = (T[]) Array.newInstance(withIds.getClass().getComponentType(), withIds.length);
        for (int i = 0;i < withIds.length;++i) {
            if (withIds[i] != null) {
                result[i] = (T) withIds[i].clone();
                result[i].id = 4;
            }
        }
        return result;
    }
}
