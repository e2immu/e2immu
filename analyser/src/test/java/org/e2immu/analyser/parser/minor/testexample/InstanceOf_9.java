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


public class InstanceOf_9 {

    interface Expression {
    }

    record StringConstant(String s) implements Expression {
    }

    record IntConstant(int i) implements Expression {
    }

    record BooleanConstant(boolean b) implements Expression {
    }

    static Expression create(Object object) {
        if (object instanceof String string) return new StringConstant(string);
        if (object instanceof Boolean bool) return new BooleanConstant(bool);
        if (object instanceof Integer integer) return new IntConstant(integer);
        throw new UnsupportedOperationException();
    }

}
