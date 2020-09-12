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

import static org.e2immu.analyser.pattern.PatternDSL.*;

public class LoopToStream {

    public static <T> void findFirst() {
        Collection<T> collection = someExpression(Collection.class, nonModifying());
        Class<T> classT = classOfTypeParameter(0);
        T someExpression = someExpression(classT, nonModifying());
        Statement someStatements = someStatements(noModificationOf(collection));

        pattern(() -> {
            T t = null;
            for (T c : collection) {
                if (c.equals(someExpression)) {
                    t = c;
                    break;
                }
            }
            if (t != null) {
                detect(someStatements, occurs(t, 1));
            }
        }, () -> collection.stream().filter(c -> c.equals(someExpression)).findFirst().ifPresent(t -> replace(someStatements)));
    }
}
