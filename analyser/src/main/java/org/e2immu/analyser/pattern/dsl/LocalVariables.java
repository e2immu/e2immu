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
        Statement someStatements = someStatements();

        pattern(() -> {
            T t = someExpression;
            detect(someStatements, occurs(0, t, 1, 1), untilEndOfBlock());
        }, () -> {
            replace(someStatements, occurrence(0, someExpression));
        });
    }

    // important: when there are 2 assignments, we do not want this pattern to keep on hopping between the two!

    public static <T> void moveVariableCloser() {
        Class<T> classT = classOfTypeParameter(0);
        T someExpression = someExpression(classT, nonModifying());
        Statement someStatements1 = someStatements();
        Statement someStatements2 = someStatements();

        pattern(() -> {
            T t = someExpression;
            detect(someStatements1, avoid(t));
            detect(someStatements2, occurs(0, t, 2)); // minimum 2 because singleUseLocalVariable picks up max 1
        }, () -> {
            replace(someStatements1);
            T t = someExpression;
            replace(someStatements2);
        });
    }

    public static <T> void moveVariableIntoBlock() {
        Class<T> classT = classOfTypeParameter(0);
        T someExpression = someExpression(classT, nonModifying());
        Statement someStatements1 = someStatements();
        Statement someStatements2 = someStatements();
        Statement someStatements3 = someStatements();
        pattern(() -> {
            T t = someExpression;
            detect(someStatements1, avoid(t));
            startBlock(someStatements2, avoid(t));
            detect(someStatements3, occurs(0, t, 1));
            endBlock();
        }, () -> {
            replace(someStatements1);
            startBlock(someStatements2);
            T t = someExpression;
            replace(someStatements3);
            endBlock();
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

    public static <T> void conditionalAssignment2() {
        Class<T> classT = classOfTypeParameter(0);
        boolean someCondition = someExpression(Boolean.class);
        T someExpression = someExpression(classT);
        T someOtherExpression = someExpression(classT);

        pattern(() -> {
            T t = null;
            if (someCondition) {
                t = someExpression;
            } else {
                t = someOtherExpression;
            }
        }, () -> {
            T t = someCondition ? someExpression : someOtherExpression;
        });
    }

    public static <T> void conditionalAssignment3() {
        Class<T> classT = classOfTypeParameter(0);
        boolean someCondition = someExpression(Boolean.class);
        T someExpression = someExpression(classT);
        T someOtherExpression = someExpression(classT);
        Statement someStatements1 = someStatements();
        Statement someStatements2 = someStatements();
        Statement someStatements3 = someStatements();
        Statement someStatements4 = someStatements();

        pattern(() -> {
            T t = null;
            if (someCondition) {
                detect(someStatements1, avoid(t));
                t = someExpression;
                detect(someStatements2);
            } else {
                detect(someStatements3, avoid(t));
                t = someOtherExpression;
                detect(someStatements4);
            }
        }, () -> {
            T t;
            if (someCondition) {
                detect(someStatements1);
                t = someExpression;
                detect(someStatements2);
            } else {
                detect(someStatements3);
                t = someOtherExpression;
                detect(someStatements4);
            }
        });
    }
}
