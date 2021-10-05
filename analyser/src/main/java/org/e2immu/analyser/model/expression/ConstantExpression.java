/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;

public interface ConstantExpression<T> extends Expression {

    @Override
    default EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
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
    default int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        switch (variableProperty) {
            case CONTAINER:
                return Level.TRUE;
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE;
            case NOT_NULL_EXPRESSION:
                return MultiLevel.EFFECTIVELY_NOT_NULL;
            case MODIFIED_METHOD:
            case CONTEXT_MODIFIED_DELAY:
            case PROPAGATE_MODIFICATION_DELAY:
            case IGNORE_MODIFICATIONS:
            case IDENTITY:
                return Level.FALSE;
            case INDEPENDENT:
                return MultiLevel.INDEPENDENT;
        }
        throw new UnsupportedOperationException("No info about " + variableProperty + " for value " + getClass());
    }

    static Expression nullValue(Primitives primitives, TypeInfo typeInfo) {
        if (typeInfo != null) {
            if (Primitives.isBoolean(typeInfo)) return new BooleanConstant(primitives, false);
            if (Primitives.isInt(typeInfo)) return new IntConstant(primitives, 0);
            if (Primitives.isLong(typeInfo)) return new LongConstant(primitives, 0L);
            if (Primitives.isShort(typeInfo)) return new ShortConstant(primitives, (short) 0);
            if (Primitives.isByte(typeInfo)) return new ByteConstant(primitives, (byte) 0);
            if (Primitives.isFloat(typeInfo)) return new FloatConstant(primitives, 0);
            if (Primitives.isDouble(typeInfo)) return new DoubleConstant(primitives, 0);
            if (Primitives.isChar(typeInfo)) return new CharConstant(primitives, '\0');
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

    @Override
    default Expression translate(TranslationMap translationMap) {
        return this;
    }
}
