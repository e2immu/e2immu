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

package org.e2immu.analyser.testexample;

import org.e2immu.annotation.E1Container;
import org.e2immu.annotation.Modified;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Container_8 {
    @E1Container
    interface Array<T> {
        int length();

        T get(int index);

        @Modified
        void set(int index, T t);
    }

    @E1Container
    static class IntArray implements Array<Integer> {
        private final ByteBuffer byteBuffer;
        private final int size;

        public IntArray(int size) {
            this.size = size;
            byteBuffer = ByteBuffer.wrap(new byte[size * Integer.BYTES]);
        }

        @Override
        public int length() {
            return size;
        }

        @Override
        public Integer get(int index) {
            return byteBuffer.getInt(index * Integer.BYTES);
        }

        @Override
        @Modified
        public void set(int index, Integer i) {
            byteBuffer.putInt(index * Integer.BYTES, i);
        }
    }

    @Test
    public void test() {
        IntArray ia = new IntArray(5);
        for (int i = 0; i < 5; i++) ia.set(i, i + 1);
        assertEquals(3, ia.get(2));
    }
}
