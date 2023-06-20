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

public enum Precedence {

    TOP(20), // constants
    ARRAY_ACCESS(19), // method invoke, object member access [] () .
    PLUSPLUS(18), // unary -, + and ++, --
    UNARY(17), // ! ~ (cast) new
    MULTIPLICATIVE(16), // * % /
    ADDITIVE(15), // + -
    STRING_CONCAT(14), // +
    SHIFT(13), // << >> >>>
    RELATIONAL(12, 0), // < <= > >=
    INSTANCE_OF(11), // instanceof
    EQUALITY(10, 0), // == !=
    AND(9), // &
    XOR(8), // ^
    OR(7), // |
    LOGICAL_AND(6), // &&
    LOGICAL_OR(5), // ||
    TERNARY(4), // ?:
    ASSIGNMENT(3), // =
    COMPOUND_ASSIGNMENT_1(2), // += -= *= %= /= &=
    COMPOUND_ASSIGNMENT_2(1), // ^= |= <<= >>= >>>=
    BOTTOM(0) //
    ;

    private final int value;
    private final int complexity;

    Precedence(int value) {
        this(value, 1);
    }

    Precedence(int value, int complexity) {
        this.value = value;
        this.complexity = complexity;
    }

    public boolean greaterThan(Precedence p) {
        return value > p.value;
    }

    public int getComplexity() {
        return complexity;
    }
}
