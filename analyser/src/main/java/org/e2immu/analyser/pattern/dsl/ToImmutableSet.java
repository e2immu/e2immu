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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.e2immu.analyser.pattern.PatternDSL.*;


public class ToImmutableSet {

    /*
     Replace Set set = new HashSet; set.add("a"); set.add("b"); use set in non-modifying way
     By: Set set = Set.of("a", "b"); use set in non-modifying way
     */
    public <T, U> void createConstantSet() {
        Class<T> clazzT = classOfTypeParameter(0);
        T someExpression = someExpression(clazzT, multiple());

        Statement someStatements = someStatements(multiple());
        Statement someStatements2 = someStatements();

        Statement returnStatement = returnStatement();

        pattern(() -> {
            Set<T> set = new HashSet<>();
            multiple(0, () -> {
                detect(someStatements, avoid(set));
                set.add(someExpression);
            });
            detect(someStatements2, noModificationOf(set));
            scan(returnStatement, expressionOfStatement(independentOf(set)));
        }, () -> {
            replace(someStatements, expand(0));
            Set<T> set = Set.of(expandParameters(someExpression, 0));
            replace(someStatements2);
        });
    }

    /*
    Replace: Set set = new Set(); Collections.addAll(set, "a"); Collections.addAll(set, "b", "c");
    By: Set set = Set.of("a", "b", "c"); ...
     */
    public <T, U> void createConstantSetWithCollectionsAddAll() {
        Class<T> clazzT = classOfTypeParameter(0);
        T someExpression = someExpression(clazzT, multiple(2));

        Statement someStatements = someStatements(multiple());
        Statement someStatements2 = someStatements();

        Statement returnStatement = returnStatement();

        pattern(() -> {
            Set<T> set = new HashSet<>();
            multiple(1, () -> {
                detect(someStatements, avoid(set));
                Collections.addAll(set, multipleParameters(0, someExpression));
            });
            detect(someStatements2, noModificationOf(set));
            scan(returnStatement, expressionOfStatement(independentOf(set)));
        }, () -> {
            replace(someStatements, expand(1));
            Set<T> set = Set.of(expandParameters(someExpression, 0, 1));
            replace(someStatements2);
        });
    }
}
