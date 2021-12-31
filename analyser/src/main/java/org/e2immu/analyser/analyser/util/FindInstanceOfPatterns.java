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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;

/*
if(x instanceof Y y) --> positive (available in 'then')
if(!(x instanceof Y y)) --> negative (available in 'else')
if(x instanceof Y y && y instanceof Z z) --> concatenate, 2x positive
 */
public class FindInstanceOfPatterns {

    public record InstanceOfPositive(InstanceOf instanceOf, boolean positive) {
    }

    public static List<InstanceOfPositive> find(Expression expression) {
        if (expression instanceof PropertyWrapper pw) return find(pw.expression());
        if (expression instanceof EnclosedExpression ee) return find(ee.inner());

        if (expression instanceof Negation negation) {
            return find(negation.expression).stream()
                    .map(iop -> new InstanceOfPositive(iop.instanceOf, !iop.positive)).toList();
        }
        // expression has most likely not been evaluated yet, so ! can be negation or unary !
        if (expression instanceof UnaryOperator unaryOperator
                && unaryOperator.operator.isUnaryNot()) {
            return find(unaryOperator.expression).stream()
                    .map(iop -> new InstanceOfPositive(iop.instanceOf, !iop.positive)).toList();
        }
        if (expression instanceof InstanceOf instanceOf) {
            return List.of(new InstanceOfPositive(instanceOf, true));
        }
        if (expression instanceof And and) {
            return and.getExpressions().stream().flatMap(e -> find(e).stream()).toList();
        }
        if (expression instanceof BinaryOperator binaryOperator
                && binaryOperator.operator.isBinaryAnd()) {
            return ListUtil.immutableConcat(find(binaryOperator.lhs), find(binaryOperator.rhs));
        }
        return List.of();
    }
}
