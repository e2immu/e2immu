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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.value.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.SetUtil;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class StringConcat extends BinaryOperator {
    private final Primitives primitives;

    private StringConcat(Primitives primitives, Expression lhs, Expression rhs, ObjectFlow objectFlow) {
        super(lhs, primitives.plusOperatorInt, rhs, BinaryOperator.ADDITIVE_PRECEDENCE, objectFlow);
        this.primitives = primitives;
    }

    public static Expression stringConcat(EvaluationContext evaluationContext, Expression l, Expression r, ObjectFlow objectFlow) {
        StringConstant lsv = l.asInstanceOf(StringConstant.class);
        StringConstant rsv = r.asInstanceOf(StringConstant.class);
        Primitives primitives = evaluationContext.getPrimitives();

        if (lsv != null && rsv != null) {
            return lsv.constant().isEmpty() ? r : rsv.constant().isEmpty() ? l :
                    new StringConstant(primitives, lsv.constant() + rsv.constant(), objectFlow);
        }
        ConstantExpression<?> rcv = r.asInstanceOf(ConstantExpression.class);
        if (lsv != null && rcv != null) {
            return new StringConstant(primitives, lsv.constant() + rcv.toString(), objectFlow);
        }
        ConstantExpression<?> lcv = l.asInstanceOf(ConstantExpression.class);
        if (rsv != null && lcv != null) {
            return new StringConstant(primitives, lcv.toString() + rsv.constant(), objectFlow);
        }
        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) return PrimitiveExpression.PRIMITIVE_EXPRESSION;

        return new StringConcat(primitives, l, r, objectFlow);
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        return switch (variableProperty) {
            case CONTAINER -> Level.TRUE;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case NOT_NULL -> MultiLevel.EFFECTIVELY_NOT_NULL;
            default -> Level.FALSE;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringConcat orValue = (StringConcat) o;
        return lhs.equals(orValue.lhs) &&
                rhs.equals(orValue.rhs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lhs, rhs);
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return lhs.print(printMode) + " + " + rhs.print(printMode);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_SUM;
    }

    @Override
    public ParameterizedType type() {
        return primitives.stringParameterizedType;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public Instance getInstance(EvaluationContext evaluationContext) {
        return new Instance(type(), getObjectFlow(), EmptyExpression.EMPTY_EXPRESSION);
    }
}
