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

package org.e2immu.analyser.parser.conditional.testexample;

public class SwitchExpression_4 {

    enum Choice {
        ONE, TWO, THREE
    }

    public static String method(Choice c, boolean b) {
        return switch (c) {
            case ONE -> "a";
            //case TWO -> {       crashes JavaParser 3.24.1
            //    yield b+"";
            //}
            case TWO -> "b";
            case THREE -> {
                System.out.println("?");
                if (b) {
                    yield "It's " + c;
                } else {
                    yield "or " + c;
                }
            }
        };
    }

}

