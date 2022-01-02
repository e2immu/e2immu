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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.TypeInfo;
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
    default DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        switch (property) {
            case CONTAINER:
                return DV.TRUE_DV;
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;
            case NOT_NULL_EXPRESSION:
                return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            case MODIFIED_METHOD:
            case IGNORE_MODIFICATIONS:
            case IDENTITY:
                return DV.FALSE_DV;
            case INDEPENDENT:
                return MultiLevel.INDEPENDENT_DV;
        }
        throw new UnsupportedOperationException("No info about " + property + " for value " + getClass());
    }

    static Expression nullValue(Primitives primitives, TypeInfo typeInfo) {
        if (typeInfo != null) {
            if (typeInfo.isBoolean()) return new BooleanConstant(primitives, false);
            if (typeInfo.isInt()) return new IntConstant(primitives, 0);
            if (typeInfo.isLong()) return new LongConstant(primitives, 0L);
            if (typeInfo.isShort()) return new ShortConstant(primitives, (short) 0);
            if (typeInfo.isByte()) return new ByteConstant(primitives, (byte) 0);
            if (typeInfo.isFloat()) return new FloatConstant(primitives, 0);
            if (typeInfo.isDouble()) return new DoubleConstant(primitives, 0);
            if (typeInfo.isChar()) return new CharConstant(primitives, '\0');
        }
        return NullConstant.NULL_CONSTANT;
    }

    static Expression create(Primitives primitives, Object object) {
        if (object instanceof String string) return new StringConstant(primitives, string);
        if (object instanceof Boolean bool) return new BooleanConstant(primitives, bool);
        if (object instanceof Integer integer) return new IntConstant(primitives, integer);
        throw new UnsupportedOperationException();
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

    @Override
    default boolean cannotHaveState() {
        return true;
    }
}
