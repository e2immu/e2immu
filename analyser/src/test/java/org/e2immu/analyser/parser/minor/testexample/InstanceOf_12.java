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

// (for now failing) attempt at recreation of a problem with BitwiseAnd.bitwiseAnd
public class InstanceOf_12 {

    interface Expression {
    }

    record Numeric(Number n) implements Expression {
        double doubleValue() {
            return n.doubleValue();
        }
    }

    record BitwiseAnd(Expression lhs, Expression rhs) implements Expression {
        // we try to maintain a sum of products
        public static Expression bitwiseAnd(Expression l, Expression r) {
            if (l instanceof Numeric ln && ln.doubleValue() == 0) return l;
            if (r instanceof Numeric rn && rn.doubleValue() == 0) return r;

            return new BitwiseAnd(l, r);
        }
    }
}
