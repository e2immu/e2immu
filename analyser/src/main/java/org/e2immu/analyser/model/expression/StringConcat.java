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
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.parser.Primitives;

public class StringConcat extends BinaryOperator {

    private StringConcat(Identifier identifier, Primitives primitives, Expression lhs, Expression rhs) {
        super(identifier, primitives, lhs, primitives.plusOperatorInt, rhs, Precedence.STRING_CONCAT);
    }

    public static Expression stringConcat(Identifier identifier, EvaluationContext evaluationContext, Expression l, Expression r) {
        StringConstant lsv = l.asInstanceOf(StringConstant.class);
        StringConstant rsv = r.asInstanceOf(StringConstant.class);
        Primitives primitives = evaluationContext.getPrimitives();

        if (lsv != null && rsv != null) {
            return lsv.constant().isEmpty() ? r : rsv.constant().isEmpty() ? l :
                    new StringConstant(primitives, lsv.constant() + rsv.constant());
        }
        ConstantExpression<?> rcv = r.asInstanceOf(ConstantExpression.class);
        if (lsv != null && rcv != null) {
            return new StringConstant(primitives, lsv.constant() + rcv);
        }
        ConstantExpression<?> lcv = l.asInstanceOf(ConstantExpression.class);
        if (rsv != null && lcv != null) {
            return new StringConstant(primitives, lcv + rsv.constant());
        }
        // any unknown lingering
        if (l.isUnknown() || r.isUnknown()) throw new UnsupportedOperationException();

        return new StringConcat(identifier, primitives, l, r);
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        return switch (property) {
            case CONTAINER -> Level.TRUE_DV;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;
            case NOT_NULL_EXPRESSION -> MultiLevel.EFFECTIVELY_NOT_NULL_DV;
            case INDEPENDENT -> MultiLevel.INDEPENDENT_DV;
            default -> Level.FALSE_DV;
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
    public int order() {
        return ExpressionComparator.ORDER_SUM;
    }

    @Override
    public Expression removeAllReturnValueParts() {
        boolean removeLhs = lhs.isReturnValue();
        boolean removeRhs = rhs.isReturnValue();
        if (removeLhs && removeRhs) return lhs; // nothing we can do
        if (removeLhs) return rhs;
        if (removeRhs) return lhs;
        return new StringConcat(identifier, primitives, lhs.removeAllReturnValueParts(), rhs.removeAllReturnValueParts());
    }
}
