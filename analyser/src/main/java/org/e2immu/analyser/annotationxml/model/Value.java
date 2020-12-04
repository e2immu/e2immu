/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.annotationxml.model;

import org.e2immu.analyser.model.Constant;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.annotation.E2Immutable;

@E2Immutable
public class Value {
    public final String name; // can be null, technically == "value", the default
    public final String val;

    public Value(String valName, String valValue) {
        name = valName;
        val = valValue;
    }

    public Value(Expression expression) {
        if (expression instanceof Constant) {
            name = null; // default name = 'value'
            val = expression.minimalOutput(); // "abc", 3.14, true, 'C'
        } else if (expression instanceof MemberValuePair mvp) {
            name = mvp.name();
            val = mvp.value().minimalOutput(); // Constant, VariableExpression, FieldAccess
        } else throw new UnsupportedOperationException("Did not expect expression of type " + expression.getClass());
    }
}
