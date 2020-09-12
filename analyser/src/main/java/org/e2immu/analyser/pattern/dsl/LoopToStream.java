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
import java.util.function.Function;
import java.util.function.Supplier;

import static org.e2immu.analyser.pattern.PatternDSL.*;

public class LoopToStream {

    public static <T> void findFirst() {
        Collection<T> collection = someExpression(Collection.class, nonModifying());
        Function<T, Boolean> someCondition = someExpressionDependentOn(Boolean.class);
        Statement someStatements = someStatements(noModificationOf(collection));
        Statement someStatementsA = someStatements(noModificationOf(collection), noBreakContinueReturn());
        Statement someStatementsB = someStatements(noModificationOf(collection), noBreakContinueReturn());

        RuntimeException newException = newException();

        Supplier<T> subPattern = subPattern(() -> {
            T t = null;
            for (T c : collection) {
                if (someCondition.apply(c)) {
                    detect(someStatementsA, occurs(0, c, 0), noModificationOf(c));
                    t = c;
                    detect(someStatementsB, noModificationOf(c));
                    break;
                }
            }
            // not part of the pattern, part of the subPattern logic
            return t;
        });

        pattern(() -> {
            T t = subPattern.get();
            if (t != null) {
                detect(someStatements, occurs(0, t, 1), untilEndOfBlock());
            }
        }, () -> collection.stream().filter(c -> someCondition.apply(c)).findFirst().ifPresent(t -> {
            replace(someStatementsA, occurrence(0, t));
            replace(someStatementsB);
            replace(someStatements);
        }));

        // NOTE that "elseException" transforms
        //    if(t != null) { someStatements; } else throw newException
        // to the following pattern:

        pattern(() -> {
            T t = subPattern.get();
            if (t == null) {
                throw newException;
            }
            detect(someStatements, occurs(0, t, 1), untilEndOfBlock());
        }, () -> {
            T t = collection.stream().filter(c -> someCondition.apply(c)).findFirst().orElseThrow(() -> newException);
            replace(someStatementsA, occurrence(0, t));
            replace(someStatementsB);
            replace(someStatements);
        });
    }
}
