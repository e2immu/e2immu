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

import java.util.function.Function;

import static org.e2immu.analyser.pattern.PatternDSL.*;

public class LocalVariables {

    public static <T> void singleUseLocalVariable() {
        Class<T> classT = classOfTypeParameter(0);
        T someExpression = someExpression(classT, nonModifying());
        Statement someStatements1 = someStatements();
        Statement someStatement = someStatements();
        Statement someStatements2 = someStatements(untilEndOfBlock(), atLeast(1));

        pattern(() -> {
            T t = someExpression;
            detect(someStatements1, avoid(t));
            detect(someStatement, occurs(t, 1, 1));
            detect(someStatements2, avoid(t));
        }, () -> {
            replace(someStatements1);
            replace(someStatement); // TODO this needs to be better
            replace(someStatements2);
        });
    }

    public static <T> void conditionalAssignment1() {
        Class<T> classT = classOfTypeParameter(0);
        T someExpression = someExpression(classT);
        Function<T, Boolean> someCondition = someExpressionDependentOn(Boolean.class, someExpression);
        T someOtherExpression = someExpression(classT);

        pattern(() -> {
            T lv = someExpression;
            if (someCondition.apply(lv)) {
                lv = someOtherExpression;
            }
        }, () -> {
            T tmp = someExpression;
            T lv = someCondition.apply(tmp) ? someOtherExpression : tmp;
        });
    }
}
