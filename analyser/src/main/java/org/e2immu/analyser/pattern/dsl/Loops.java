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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import static org.e2immu.analyser.pattern.PatternDSL.*;

public class Loops {

    public static <T> void basicForEach() {
        List<T> list = someVariable(List.class);
        Statement someStatements = someStatements(noModificationOf(list));

        pattern(() -> {
            for (int i = 0; i < list.size(); i++) {
                detect(someStatements, occurs(0, list.get(i)));
            }
        }, () -> {
            for (T t : list) {
                replace(someStatements, occurrence(0, t));
            }
        });
    }

    public static <T> void iteratorToForEach() {
        Collection<T> collection = someExpression(Collection.class);
        Statement someStatements1 = someStatements(noModificationOf(collection));
        Statement someStatements2 = someStatements(noModificationOf(collection));

        pattern(() -> {
            Iterator<T> iterator = collection.iterator();
            while (iterator.hasNext()) {
                detect(someStatements1);
                T t = iterator.next();
                detect(someStatements2);
            }
        }, () -> {
            for (T t : collection) {
                replace(someStatements1);
                replace(someStatements2);
            }
        });
    }


    public static <T> void iteratorToForEach2() {
        Collection<T> collection = someExpression(Collection.class);
        Statement someStatements1 = someStatements(noModificationOf(collection));

        pattern(() -> {
            Iterator<T> iterator = collection.iterator();
            while (iterator.hasNext()) {
                detect(someStatements1, occurs(0, iterator.next(), 1, 1));
            }
        }, () -> {
            for (T t : collection) {
                replace(someStatements1, occurrence(0, t));
            }
        });
    }

    public static <T> void iteratorInForToForEach() {
        Collection<T> collection = someExpression(Collection.class);
        Statement someStatements1 = someStatements(noModificationOf(collection));
        Statement someStatements2 = someStatements(noModificationOf(collection));

        pattern(() -> {
            for (Iterator<T> iterator = collection.iterator(); iterator.hasNext(); ) {
                detect(someStatements1, avoid(iterator));
                T t = iterator.next();
                detect(someStatements2, occurs(0, t, 1), avoid(iterator));
            }
        }, () -> {
            for (T t : collection) {
                replace(someStatements1);
                replace(someStatements2);
            }
        });
    }

    public static <T> void iteratorInForToForEach2() {
        Collection<T> collection = someExpression(Collection.class);
        Statement someStatements1 = someStatements(noModificationOf(collection));

        pattern(() -> {
            for (Iterator<T> iterator = collection.iterator(); iterator.hasNext(); ) {
                detect(someStatements1, occurs(0, iterator.next(), 1, 1), avoid(iterator.hasNext()));
            }
        }, () -> {
            for (T t : collection) {
                replace(someStatements1, occurrence(0, t));
            }
        });
    }

}
