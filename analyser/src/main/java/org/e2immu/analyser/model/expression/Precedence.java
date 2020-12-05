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
    RELATIONAL(12), // < <= > >=
    INSTANCE_OF(11), // instanceof
    EQUALITY(10), // == !=
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

    Precedence(int value) {
        this.value = value;
    }

    public boolean greaterThan(Precedence p) {
        return value > p.value;
    }

}
