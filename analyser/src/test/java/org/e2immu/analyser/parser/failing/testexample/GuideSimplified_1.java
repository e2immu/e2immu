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

package org.e2immu.analyser.parser.failing.testexample;


import java.util.concurrent.atomic.AtomicInteger;

public record GuideSimplified_1(int index,
                                Position position,
                                int tabs,
                                boolean prioritySplit,
                                boolean startWithNewLine,
                                boolean endWithNewLine,
                                boolean allowNewLineBefore) {
    private static final AtomicInteger generator = new AtomicInteger();

    public enum Position {
        START("S"), MID(""), END("E");

        private final String msg;

        Position(String msg) {
            this.msg = msg;
        }
    }

    public static String minimal() {
        return "";
    }

    public static String debug() {
        return "";
    }

    public String trace() {
        return "/*" + position.msg + index + "*/";
    }


    public String generateJavaForDebugging() {
        return ".add(gg" + index + "." + (switch (position) {
            case START -> "start";
            case MID -> "mid";
            case END -> "end";
        }) + "()) // priority=" + prioritySplit + ", startNL=" + startWithNewLine + ", endNL=" + endWithNewLine;
    }
}
