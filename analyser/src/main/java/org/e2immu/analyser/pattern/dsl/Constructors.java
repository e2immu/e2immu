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

import static org.e2immu.analyser.pattern.PatternDSL.*;

public class Constructors<T> {

    private final Class<T> classT = classOfTypeParameter(0);
    private T t = someVariable(classT);

    public void firstEscapeThenAssign() {
        inConstructorsOnly();

        T t = someVariable(classT);
        boolean someConditionOnThisT = someExpression(Boolean.class, noModificationOf(this.t, t), occurs(0, this.t));
        RuntimeException someException = newException();
        Statement someStatements = someStatements(noModificationOf(t, this.t), occurs(1, this.t, 0));

        pattern(() -> {
            this.t = t;
            detect(someStatements);
            if (someConditionOnThisT) {
                throw someException;
            }
        }, () -> {
            if (replace(someConditionOnThisT, occurrence(0, t))) {
                throw someException;
            }
            replace(someStatements, occurrence(1, t));
            this.t = t;
        });
    }

    public void avoidReassigningToFields() {
        inConstructorsOnly();

        T someExpression = someExpression(classT, avoid(this.t));
        Statement someStatements = someStatements(occurs(0, this.t, 0), noCallsToMethodsAccessing(this.t));
        T someOtherExpression = someExpression(classT, occurs(1, this.t, 0));

        pattern(() -> {
            this.t = someExpression;
            detect(someStatements);
            this.t = someOtherExpression;
        }, () -> {
            T tmp = someExpression;
            replace(someStatements, occurrence(0, tmp));
            this.t = replace(someOtherExpression, occurrence(1, tmp));
        });
    }
}
