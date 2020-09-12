/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.pattern.dsl;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import static org.e2immu.analyser.pattern.PatternDSL.*;

public class Enumerations {

    public static <T> void enumerationHelper() {
        Enumeration<T> someEnumeration = someExpression(Enumeration.class);
        Statement someStatements1 = someStatements();
        Statement someStatements2 = someStatements();

        warn(() -> {
            Enumeration<T> enumeration = someEnumeration;
            while (enumeration.hasMoreElements()) {
                detect(someStatements1, occurs(0, enumeration, 1));
                T t = enumeration.nextElement();
                detect(someStatements2, avoid(enumeration));
            }
        });
        warn(() -> {
            Enumeration<T> enumeration = someEnumeration;
            while (enumeration.hasMoreElements()) {
                detect(someStatements1, avoid(enumeration));
                T t = enumeration.nextElement();
                detect(someStatements2, occurs(0, enumeration, 1));
            }
        });
        pattern(() -> {
            Enumeration<T> enumeration = someEnumeration;
            while (enumeration.hasMoreElements()) {
                detect(someStatements1, avoid(enumeration));
                T t = enumeration.nextElement();
                detect(someStatements2, avoid(enumeration));
            }
        }, () -> {
            for (Enumeration<T> enumeration = someEnumeration; enumeration.hasMoreElements(); ) {
                replace(someStatements1);
                T t = enumeration.nextElement();
                replace(someStatements2);
            }
        });
    }

    public static <T> void vectorEnumerationToForEach() {
        Vector<T> vector = someExpression(Vector.class);
        Statement someStatements1 = someStatements();
        Statement someStatements2 = someStatements();

        pattern(() -> {
            for (Enumeration<T> e = vector.elements(); e.hasMoreElements(); ) {
                detect(someStatements1);
                T t = e.nextElement();
                detect(someStatements2);
            }
        }, () -> {
            for (T t : vector) {
                replace(someStatements1);
                replace(someStatements2);
            }
        });
    }

    // also: check synchronization, but Hashtable -> HashMap OR ConcurrentHashMap

    public static <K, V> void hashTableKeysEnumerationToForEach() {
        Hashtable<K, V> hashTable = someExpression(Hashtable.class);
        Statement someStatements1 = someStatements();
        Statement someStatements2 = someStatements();

        pattern(() -> {
            for (Enumeration<K> e = hashTable.keys(); e.hasMoreElements(); ) {
                detect(someStatements1, avoid(e));
                K k = e.nextElement();
                detect(someStatements2, avoid(e));
            }
        }, () -> {
            for (K k : hashTable.keySet()) {
                replace(someStatements1);
            }
        });

        pattern(() -> {
            for (Enumeration<V> e = hashTable.elements(); e.hasMoreElements(); ) {
                detect(someStatements1, avoid(e));
                V v = e.nextElement();
                detect(someStatements2, avoid(e));
            }
        }, () -> {
            for (V v : hashTable.values()) {
                replace(someStatements1);
            }
        });
    }
}
