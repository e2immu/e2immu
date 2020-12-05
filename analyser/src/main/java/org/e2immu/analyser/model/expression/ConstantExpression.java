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
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;

public interface ConstantExpression<T> extends Expression {

    @Override
    default EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        builder.createLiteralObjectFlow(returnType());
        return builder.setExpression(this).build();
    }

    @Override
    default EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        return new EvaluationResult.Builder().setExpression(this).build();
    }

    T getValue();

    @Override
    default Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    default boolean isConstant() {
        return true;
    }

    @Override
    default NewObject getInstance(EvaluationContext evaluationContext) {
        return null;
    }

    @Override
    default int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        switch (variableProperty) {
            case CONTAINER:
                return Level.TRUE;
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_E2IMMUTABLE;
            case NOT_NULL:
                return MultiLevel.EFFECTIVELY_NOT_NULL;
            case MODIFIED:
            case NOT_MODIFIED_1:
            case METHOD_DELAY:
            case IGNORE_MODIFICATIONS:
            case IDENTITY:
                return Level.FALSE;
        }
        throw new UnsupportedOperationException("No info about " + variableProperty + " for value " + getClass());
    }

    static Expression nullValue(Primitives primitives, TypeInfo typeInfo) {
        if (typeInfo != null) {
            if (Primitives.isBoolean(typeInfo)) return new BooleanConstant(primitives, false, ObjectFlow.NO_FLOW);
            if (Primitives.isInt(typeInfo)) return new IntConstant(primitives, 0, ObjectFlow.NO_FLOW);
            if (Primitives.isLong(typeInfo)) return new LongConstant(primitives, 0L, ObjectFlow.NO_FLOW);
            if (Primitives.isShort(typeInfo)) return new ShortConstant(primitives, (short) 0, ObjectFlow.NO_FLOW);
            if (Primitives.isByte(typeInfo)) return new ByteConstant(primitives, (byte) 0, ObjectFlow.NO_FLOW);
            if (Primitives.isFloat(typeInfo)) return new FloatConstant(primitives, 0, ObjectFlow.NO_FLOW);
            if (Primitives.isDouble(typeInfo)) return new DoubleConstant(primitives, 0, ObjectFlow.NO_FLOW);
            if (Primitives.isChar(typeInfo)) return new CharConstant(primitives, '\0', ObjectFlow.NO_FLOW);
        }
        return NullConstant.NULL_CONSTANT;
    }

    static Expression equalsExpression(Primitives primitives, ConstantExpression<?> l, ConstantExpression<?> r) {
        if (l instanceof NullConstant || r instanceof NullConstant)
            throw new UnsupportedOperationException("Not for me");

        if (l instanceof StringConstant ls && r instanceof StringConstant rs) {
            return new BooleanConstant(primitives, ls.constant().equals(rs.constant()));
        }
        if (l instanceof BooleanConstant lb && r instanceof BooleanConstant lr) {
            return new BooleanConstant(primitives, lb.constant() == lr.constant());
        }
        if (l instanceof CharConstant lc && r instanceof CharConstant rc) {
            return new BooleanConstant(primitives, lc.constant() == rc.constant());
        }
        if (l instanceof Numeric ln && r instanceof Numeric rn) {
            return new BooleanConstant(primitives, ln.getNumber().equals(rn.getNumber()));
        }
        throw new UnsupportedOperationException("l = " + l + ", r = " + r);
    }
}
