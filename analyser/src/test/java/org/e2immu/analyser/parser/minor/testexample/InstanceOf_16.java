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

package org.e2immu.analyser.parser.minor.testexample;


import org.e2immu.annotation.Modified;

import java.util.List;

public class InstanceOf_16 {

    interface Operator {
        boolean isUnaryNot();
    }

    interface Expression {
        @Modified // because List.of(....) modifies its argument, we evaluate without Annotated API
        List<Expression> subElements();
    }

    record Negation(Expression en) implements Expression {
        @Override
        public List<Expression> subElements() {
            return List.of(en);
        }
    }

    record UnaryOperator(Expression eu, Operator operator) implements Expression {
        @Override
        public List<Expression> subElements() {
            return List.of(eu);
        }
    }

    record InstanceOf(Class<?> clazz) implements Expression {
        @Override
        public List<Expression> subElements() {
            return List.of();
        }
    }

    public record InstanceOfPositive(InstanceOf instanceOf, boolean positive) {
    }

    static class FindInstanceOfPatterns {
        public static List<InstanceOfPositive> find(Expression expression) {
            if (expression instanceof Negation negation) {
                return find(negation.en).stream()
                        .map(iop -> new InstanceOfPositive(iop.instanceOf, !iop.positive)).toList();
            }
            // expression has most likely not been evaluated yet, so ! can be negation or unary !
            if (expression instanceof UnaryOperator unaryOperator && unaryOperator.operator.isUnaryNot()) {
                return find(unaryOperator.eu).stream()
                        .map(iop -> new InstanceOfPositive(iop.instanceOf, !iop.positive)).toList();
            }
            if (expression instanceof InstanceOf instanceOf) {
                return List.of(new InstanceOfPositive(instanceOf, true));
            }
            return expression.subElements().stream().flatMap(e -> find(e).stream()).toList();
        }
    }
}
