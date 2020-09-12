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

import static org.e2immu.analyser.pattern.PatternDSL.*;

public class IfStatements {

    public static <T> void twoIfsComplement() {
        boolean someCondition = someExpression(Boolean.class);
        Statement statements = someStatements();
        Statement statements2 = someStatements();

        pattern(Quality.WARN, () -> {
            if (someCondition) {
                detect(statements);
            }
            if (!someCondition) {
                detect(statements2);
            }
        }, () -> {
            if (someCondition) {
                replace(statements);
            } else {
                replace(statements2);
            }
        });
    }

    public static <T> void collapseIfElse() {
        boolean someCondition = someExpression(Boolean.class);
        Statement statements = someStatements();

        pattern(Quality.WARN, () -> {
            if (someCondition) {
                detect(statements);
            } else {
                detect(statements);
            }
        }, () -> replace(statements));
    }

    public static <T> void elseException() {
        boolean someCondition = someExpression(Boolean.class);
        RuntimeException newException = newException();
        Statement statements = someStatements();
        Statement statements2 = someStatements();

        pattern(() -> {
            if (someCondition) {
                detect(statements);
            } else {
                detect(statements2);
                throw newException;
            }
        }, () -> {
            if (!someCondition) {
                replace(statements2);
                throw newException;
            }
            replace(statements);
        });
    }

    public static <T> void ifException() {
        boolean someCondition = someExpression(Boolean.class);
        RuntimeException newException = newException();
        Statement statements = someStatements();
        Statement statements2 = someStatements();

        pattern(() -> {
            if (someCondition) {
                detect(statements2);
                throw newException;
            } else {
                detect(statements);
            }
        }, () -> {
            if (someCondition) {
                replace(statements2);
                throw newException;
            }
            replace(statements);
        });
    }

    public static <T> void twoReturns1() {
        Class<T> classT = classOfTypeParameter(0);
        T someExpression = someExpression(classT);
        boolean someCondition = someExpression(Boolean.class);
        T someOtherExpression = someExpression(classT);

        returnPattern(() -> {
            if (someCondition) return someExpression;
            return someOtherExpression;
        }, () -> {
            return someCondition ? someExpression : someOtherExpression;
        });
    }

    public static <T> void twoReturns2() {
        Class<T> classT = classOfTypeParameter(0);
        T someExpression = someExpression(classT);
        boolean someCondition = someExpression(Boolean.class);
        T someOtherExpression = someExpression(classT);

        returnPattern(() -> {
            if (someCondition) {
                return someExpression;
            } else {
                return someOtherExpression;
            }
        }, () -> {
            return someCondition ? someExpression : someOtherExpression;
        });
    }

    // this one is really bad programming

    public static <T> void sameConditionTwice() {

        boolean someCondition = someExpression(Boolean.class, nonModifying());
        boolean someOtherCondition = someExpression(Boolean.class, nonModifying());
        Statement statements1 = someStatements();
        Statement statements2 = someStatements();

        pattern(PatternDSL.Quality.WARN, () -> {
            if (someCondition) {
                if (someOtherCondition) {
                    detect(statements1);
                }
            } else {
                if (someOtherCondition) {
                    detect(statements2);
                }
            }
        }, () -> {
            if (someOtherCondition) {
                if (someCondition) {
                    detect(statements1);
                } else {
                    detect(statements2);
                }
            }
        });
    }

    // IMPLICIT: && is commutative; someCondition can consist of multiple other subexpressions
    // this makes matching across multiple statements very difficult!!

    public static <T> void sameConditionTwice2() {
        boolean someCondition = someExpression(Boolean.class);
        boolean someOtherCondition = someExpression(Boolean.class);
        boolean someThirdCondition = someExpression(Boolean.class);
        Statement statements1 = someStatements();
        Statement statements2 = someStatements();

        pattern(() -> {
            if (someCondition && someOtherCondition) {
                detect(statements1);
            }
            if (someCondition && someThirdCondition) {
                detect(statements2);
            }
        }, () -> {
            if (someCondition) {
                if (someOtherCondition) {
                    replace(statements1);
                }
                if (someThirdCondition) {
                    replace(statements2);
                }
            }
        });
    }
}
