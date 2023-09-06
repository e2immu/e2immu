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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.function.Predicate;

public interface ConstantExpression<T> extends Expression {

    @Override
    default EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        return builder.setExpression(this).build();
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
    default DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return propertyOfConstant(property);
    }

    static DV propertyOfConstant(Property property) {
        switch (property) {
            case CONTAINER:
                return MultiLevel.CONTAINER_DV;
            case IMMUTABLE:
                return MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
            case NOT_NULL_EXPRESSION:
                return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            case MODIFIED_METHOD:
            case TEMP_MODIFIED_METHOD:
            case IGNORE_MODIFICATIONS:
            case IDENTITY:
            case IMMUTABLE_BREAK:
                return property.falseDv;
            case INDEPENDENT:
                return MultiLevel.INDEPENDENT_DV;
        }
        throw new UnsupportedOperationException("No info about " + property);
    }

    static Expression nullValue(Primitives primitives, Identifier identifier, TypeInfo typeInfo) {
        if (typeInfo != null) {
            if (typeInfo.isBoolean()) return new BooleanConstant(primitives, identifier, false);
            if (typeInfo.isInt()) return new IntConstant(primitives, identifier, 0);
            if (typeInfo.isLong()) return new LongConstant(primitives, identifier, 0L);
            if (typeInfo.isShort()) return new ShortConstant(primitives, identifier, (short) 0);
            if (typeInfo.isByte()) return new ByteConstant(primitives, identifier, (byte) 0);
            if (typeInfo.isFloat()) return new FloatConstant(primitives, identifier, 0);
            if (typeInfo.isDouble()) return new DoubleConstant(primitives, identifier, 0);
            if (typeInfo.isChar()) return new CharConstant(primitives, identifier, '\0');
        }
        return new NullConstant(identifier);
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
    default Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        return translationMap.translateExpression(this);
    }

    @Override
    default boolean cannotHaveState() {
        return true;
    }

    @Override
    default void visit(Predicate<Element> predicate) {
        predicate.test(this);
    }
}
