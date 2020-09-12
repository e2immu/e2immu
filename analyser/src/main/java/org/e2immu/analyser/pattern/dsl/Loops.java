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
import java.util.stream.IntStream;

import static org.e2immu.analyser.pattern.PatternDSL.*;

public class Loops {

    public static <T> void basicForEach() {
        List<T> list = someVariable(List.class);
        Statement someStatements1 = someStatements(noModificationOf(list));
        Statement someStatements2 = someStatements(noModificationOf(list));

        pattern(() -> {
            for (int i = 0; i < list.size(); i++) {
                detect(someStatements1);
                expressionPartOfStatement(list.get(i), 0);
                detect(someStatements2);
            }
        }, () -> {
            for (T t : list) {
                replace(someStatements1);
                replaceExpressionPartOfStatement(t, 0);
                replace(someStatements2);
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
                detect(someStatements1, occurs(iterator.next(), 1, 1), avoid());
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
                detect(someStatements1);
                T t = iterator.next();
                detect(someStatements2, occurs(t, 1));
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
                detect(someStatements1, occurs(iterator.next(), 1, 1), avoid());
            }
        }, () -> {
            for (T t : collection) {
                replace(someStatements1, occurrence(0, t));
            }
        });
    }

}
