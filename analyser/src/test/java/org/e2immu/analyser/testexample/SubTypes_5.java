/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.testexample;

import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SubTypes_5 {

    public static Iterable<Integer> makeIterator(int n) {
        int lower = 0;

        // for this test, do not turn into Lambda!
        return new Iterable<>() {

            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<>() {
                    private int i = n;

                    @Override
                    public boolean hasNext() {
                        return i >= lower;
                    }

                    @Override
                    public Integer next() {
                        return i--;
                    }
                };
            }
        };
    }

    public static int sum() {
        int sum = 0;
        for (int i : makeIterator(5)) {
            sum += i;
        }
        return sum;
    }

    @Test
    public void test() {
        assertEquals(15, sum());
    }
}
