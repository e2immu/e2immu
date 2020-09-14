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

    public static <T> void introduceLocalVariable() {
        Class<T> classT = classOfTypeParameter(0);
        T someExpression = someExpression(classT, nonModifying());
        Statement someStatements1 = someStatements();

        pattern(() -> {
            detect(someStatements1, occurs(0, someExpression, 2), untilEndOfBlock());
        }, () -> {
            T t = someExpression;
            replace(someStatements1, occurrence(0, t));
        });
    }

    public static <T> void preventReuse() {
        Class<T> classT = classOfTypeParameter(0);
        T someExpression = someExpression(classT, nonModifying());
        T someOtherExpression = someExpression(classT, nonModifying());
        Statement someStatements1 = someStatements();
        Statement someStatements2 = someStatements();

        pattern(() -> {
            T t = someExpression;
            detect(someStatements1, occurs(0, t, 1));
            t = someOtherExpression;
            detect(someStatements2, occurs(1, t, 1));
        }, () -> {
            T t = someExpression;
            replace(someStatements1);
            T t2 = someOtherExpression;
            replace(someStatements2, occurrence(1, t2));
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

        pattern(() -> {
            T t;
            if (someCondition) {
                t = someExpression;
            } else {
                t = someOtherExpression;
            }
        }, () -> {
            T t = someCondition ? someExpression : someOtherExpression;
        });
    }

    // T t = null; is moved close to the if statement by the move variable closer pattern, so no need for
    // an additional statement in between

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

    /*
        TODO create pattern

        int notNull = methodInfo.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL);
        if (iteration == 0) {
            Assert.assertEquals(Level.DELAY, notNull);
        } else {
            Assert.assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, notNull);
        }

        should probably be better as

        int notNull = methodInfo.methodAnalysis.get().getProperty(VariableProperty.NOT_NULL);
        int expected = iteration == 0 ? Level.DELAY : MultiLevel. EFFECTIVELY_CONTENT_NOT_NULL;
        Assert.assertEquals(expected, notNull);

        try not to repeat more complicated expressions (here: Assert.assertEquals)
     */
}
