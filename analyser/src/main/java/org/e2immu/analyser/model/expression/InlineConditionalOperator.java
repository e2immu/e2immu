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

import com.google.common.collect.Sets;
import org.e2immu.analyser.model.EvaluationContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.abstractvalue.ConditionalValue;
import org.e2immu.analyser.model.abstractvalue.NegatedValue;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@E2Immutable
public class InlineConditionalOperator implements Expression {
    public final Expression conditional;
    public final Expression ifTrue;
    public final Expression ifFalse;

    public InlineConditionalOperator(@NullNotAllowed Expression conditional,
                                     @NullNotAllowed Expression ifTrue,
                                     @NullNotAllowed Expression ifFalse) {
        this.conditional = Objects.requireNonNull(conditional);
        this.ifFalse = Objects.requireNonNull(ifFalse);
        this.ifTrue = Objects.requireNonNull(ifTrue);
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext) {
        Value c = conditional.evaluate(evaluationContext);
        if (c == BoolValue.TRUE) return ifTrue.evaluate(evaluationContext);
        if (c == BoolValue.FALSE) return ifFalse.evaluate(evaluationContext);

        // we'll want to evaluate in a different context
        EvaluationContext copyForThen = evaluationContext.child(c);
        EvaluationContext copyForElse = evaluationContext.child(NegatedValue.negate(c));
        Value t = ifTrue.evaluate(copyForThen);
        Value f = ifFalse.evaluate(copyForElse);
        return new ConditionalValue(c, t, f);
    }

    @Override
    public ParameterizedType returnType() {
        return ifTrue.returnType();
    }

    @Override
    @NotNull
    public String expressionString(int indent) {
        return bracketedExpressionString(indent, conditional) + " ? " + bracketedExpressionString(indent, ifTrue)
                + " : " + bracketedExpressionString(indent, ifFalse);
    }

    @Override
    public int precedence() {
        return 2;
    }

    @Override
    @NotNull
    @Independent
    public Set<String> imports() {
        return Sets.union(Sets.union(conditional.imports(), ifFalse.imports()), ifTrue.imports());
    }

    @Override
    @NotNull
    @Independent
    public List<Expression> subExpressions() {
        return List.of(conditional, ifTrue, ifFalse);
    }


    @Override
    public List<InScopeSide> expressionsInScopeSide() {
        return List.of(); // dealt with separately new InScopeSide(conditional, true), new InScopeSide(ifTrue, false), new InScopeSide(ifFalse, false));
    }
}
