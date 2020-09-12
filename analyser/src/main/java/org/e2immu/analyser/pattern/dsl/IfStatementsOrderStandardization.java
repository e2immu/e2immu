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

import org.e2immu.analyser.pattern.PatternDSL;

import java.util.Collection;

import static org.e2immu.analyser.pattern.PatternDSL.*;
import static org.e2immu.analyser.pattern.PatternDSL.replace;

/**
 * standardization of the order of the if-else clauses
 * <p>
 * if(t == null) { null case } else { not null case }
 * if(collection.isEmpty()) { } else { }
 */
public class IfStatementsOrderStandardization {

    public static <T> void nullCheckAlwaysFirst() {
        Class<T> classT = classOfTypeParameter(0);
        T someExpression = someExpression(classT);
        Statement statements1 = someStatements();
        Statement statements2 = someStatements();

        pattern(() -> {
            if (someExpression != null) {
                detect(statements1);
            } else {
                detect(statements2);
            }
        }, () -> {
            if (someExpression == null) {
                replace(statements2);
            } else {
                replace(statements1);
            }
        });
    }

    // even if internally we treat it as size == 0, in the code we'd rather see the method
    public static <T> void size0isEmpty() {
        Collection<T> collection = someExpression(Collection.class, nonModifying());

        partOfExpressionPattern(collection, () -> collection.size() != 0, () -> !collection.isEmpty());
        partOfExpressionPattern(collection, () -> collection.size() > 0, () -> !collection.isEmpty());
        partOfExpressionPattern(collection, () -> collection.size() == 0, () -> collection.isEmpty());
        partOfExpressionPattern(collection, () -> collection.size() <= 0, () -> collection.isEmpty());
        partOfExpressionPattern(collection, () -> collection.size() < 1, () -> collection.isEmpty());
    }

    public static <T> void emptyCollection() {
        Collection<T> collection = someExpression(Collection.class, nonModifying());
        Statement statements1 = someStatements();
        Statement statements2 = someStatements();

        pattern(() -> {
            if (!collection.isEmpty()) {
                detect(statements1);
            } else {
                detect(statements2);
            }
        }, () -> {
            if (collection.isEmpty()) {
                detect(statements2);
            } else {
                detect(statements1);
            }
        });
    }

}
