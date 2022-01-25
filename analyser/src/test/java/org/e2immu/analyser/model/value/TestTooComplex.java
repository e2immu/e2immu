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

package org.e2immu.analyser.model.value;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.MultiExpressions;
import org.e2immu.analyser.model.expression.MultiValue;
import org.e2immu.analyser.model.expression.NullConstant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class TestTooComplex extends CommonAbstractValue {

    EvaluationContext evaluationContext = new EvaluationContextImpl() {
        @Override
        public int limitOnComplexity() {
            return 10;
        }
    };

    @Test
    public void test() {
        Expression condition1 = newAnd(newOr(a, newEquals(NullConstant.NULL_CONSTANT, s)),
                newOr(a, negate(newEquals(s1, s2))));
        assertEquals("(a||null==s)&&(a||s1!=s2)", condition1.toString());
        // 21 = (1+4+1+2+1)+3+(9)
        assertEquals(21, condition1.getComplexity());
        Expression addOne = And.and(evaluationContext, condition1, a);
        if (addOne instanceof MultiExpressions multiExpressions) {
            Expression[] expressions = multiExpressions.multiExpression.expressions();
            assertEquals(5, expressions.length);
            assertEquals("instance type boolean", expressions[4].toString());
        } else fail();
    }
}
