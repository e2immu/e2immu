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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.ArrayValue;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NullNotAllowed;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@E2Immutable
public class ArrayInitializer implements Expression {
    public final List<Expression> expressions;

    public ArrayInitializer(@NullNotAllowed @NotModified List<Expression> expressions) {
        this.expressions = Objects.requireNonNull(expressions);
    }

    @Override
    public ParameterizedType returnType() {
        return commonType(expressions);
    }

    // TODO needs better implementation!!
    private static ParameterizedType commonType(List<Expression> expressions) {
        if (expressions.isEmpty()) return Primitives.PRIMITIVES.voidParameterizedType;
        return expressions.get(0).returnType();
    }

    @Override
    public String expressionString(int indent) {
        return expressions.stream().map(e -> e.expressionString(indent)).collect(Collectors.joining(", ", "{", "}"));
    }

    @Override
    public int precedence() {
        return 1;
    }

    @Override
    public List<Expression> subExpressions() {
        return expressions;
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor) {
        List<Value> values = expressions.stream().map(e -> e.evaluate(evaluationContext, visitor)).collect(Collectors.toList());

        ArrayValue arrayValue = new ArrayValue(values);
        visitor.visit(this, evaluationContext, arrayValue);
        return arrayValue;
    }
}
