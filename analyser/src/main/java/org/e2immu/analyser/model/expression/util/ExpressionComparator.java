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

package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.ExpressionWrapper;

import java.util.Comparator;

public class ExpressionComparator implements Comparator<Expression> {
    public static final int ORDER_CONSTANT_NULL = 30;
    public static final int ORDER_CONSTANT_BOOLEAN = 31;
    public static final int ORDER_CONSTANT_BYTE = 32;
    public static final int ORDER_CONSTANT_CHAR = 33;
    public static final int ORDER_CONSTANT_SHORT = 34;
    public static final int ORDER_CONSTANT_INT = 35;
    public static final int ORDER_CONSTANT_FLOAT = 36;
    public static final int ORDER_CONSTANT_LONG = 37;
    public static final int ORDER_CONSTANT_DOUBLE = 38;
    public static final int ORDER_CONSTANT_CLASS = 39;
    public static final int ORDER_CONSTANT_STRING = 40;
    public static final int ORDER_PRODUCT = 41;
    public static final int ORDER_DIVIDE = 42;
    public static final int ORDER_REMAINDER = 43;
    public static final int ORDER_SUM = 44;
    public static final int ORDER_BITWISE_AND = 45;

    // variables, types
    public static final int ORDER_PRIMITIVE = 60;
    public static final int ORDER_ARRAY = 61;
    public static final int ORDER_INSTANCE = 63;
    public static final int ORDER_INLINE_METHOD = 64;
    public static final int ORDER_METHOD = 65;
    public static final int ORDER_VARIABLE = 66;
    public static final int ORDER_COMBINED = 67;
    public static final int ORDER_TYPE = 68;
    public static final int ORDER_NO_VALUE = 69;
    // we use the order of the conditional,but as a consequence, all internalOrders need to be able to deal
    // with an inline conditional
    //public static final int ORDER_CONDITIONAL = 70;
    public static final int ORDER_SWITCH = 71;

    // boolean operations
    public static final int ORDER_INSTANCE_OF = 81;
    public static final int ORDER_EQUALS = 82;
    public static final int ORDER_GEQ0 = 83;
    public static final int ORDER_OR = 85;
    public static final int ORDER_AND = 86;

    // must be later than any other binary operator
    public static final int ORDER_BINARY_OPERATOR = 87;

    // irrelevant, normally
    public static final int ORDER_MVP = 90;

    public static final ExpressionComparator SINGLETON = new ExpressionComparator();

    private ExpressionComparator() {
        // nothing here
    }

    private record Unwrapped(Expression value) {

        public static Unwrapped create(Expression v) {
            Expression unwrapped = v;
            while (unwrapped instanceof ExpressionWrapper e) {
                unwrapped = e.getExpression();
            }
            return new Unwrapped(unwrapped);
        }
    }

    @Override
    public int compare(Expression v1, Expression v2) {
        boolean v1Wrapped = v1 instanceof ExpressionWrapper;
        boolean v2Wrapped = v2 instanceof ExpressionWrapper;

        // short-cut
        if (!v1Wrapped && !v2Wrapped) {
            return compareWithoutWrappers(v1, v2);
        }

        Unwrapped u1 = Unwrapped.create(v1);
        Unwrapped u2 = Unwrapped.create(v2);

        int withoutWrappers = compareWithoutWrappers(u1.value, u2.value);
        if (withoutWrappers != 0) return withoutWrappers;

        if (!v1Wrapped) return -1; // unwrapped always before wrapped
        if (!v2Wrapped) return 1;

        // now both are wrapped...
        int w = ((ExpressionWrapper) v1).wrapperOrder() - ((ExpressionWrapper) v2).wrapperOrder();

        // different wrappers
        if (w != 0) return w;

        // same wrappers, go deeper
        return compare(((ExpressionWrapper) v1).getExpression(), ((ExpressionWrapper) v2).getExpression());
    }

    private int compareWithoutWrappers(Expression v1, Expression v2) {
        int orderDiff = v1.order() - v2.order();
        if (orderDiff != 0) return orderDiff;
        return v1.internalCompareTo(v2);
    }
}
