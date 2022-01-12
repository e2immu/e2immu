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

public record GuideSimplified_0(int index,
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

    public static GuideGenerator generatorForBlock() {
        return new GuideGenerator(1, true, true, true, true);
    }

    public static GuideGenerator generatorForEnumDefinitions() {
        return new GuideGenerator(0, false, false, true, true);
    }

    public static GuideGenerator generatorForCompanionList() {
        return new GuideGenerator(0, true, false, false, true);
    }

    public static GuideGenerator defaultGuideGenerator() {
        return new GuideGenerator(1, false, false, false, false);
    }

    public static GuideGenerator generatorForParameterDeclaration() {
        return new GuideGenerator(1, false, true, false, false);
    }

    public static GuideGenerator generatorForAnnotationList() {
        return new GuideGenerator(0, false, false, false, false);
    }

    public static class GuideGenerator {
        public final int index;
        private final int tabs;
        private final boolean prioritySplit;
        private final boolean startWithNewLine;
        private final boolean endWithNewLine;
        private final boolean allowNewLineBefore;

        private GuideGenerator() {
            this(1, false, false, false, false);
        }

        private GuideGenerator(int tabs, boolean prioritySplit, boolean startWithNewLine, boolean endWithNewLine, boolean allowNewLineBefore) {
            index = generator.incrementAndGet();
            this.tabs = tabs;
            this.startWithNewLine = startWithNewLine;
            this.endWithNewLine = endWithNewLine;
            this.prioritySplit = prioritySplit;
            this.allowNewLineBefore = allowNewLineBefore;
        }

        public GuideSimplified_0 start() {
            return new GuideSimplified_0(index, Position.START, tabs, prioritySplit, startWithNewLine, endWithNewLine, allowNewLineBefore);
        }

        public GuideSimplified_0 mid() {
            return new GuideSimplified_0(index, Position.MID, tabs, prioritySplit, startWithNewLine, endWithNewLine, allowNewLineBefore);
        }

        public GuideSimplified_0 end() {
            return new GuideSimplified_0(index, Position.END, tabs, prioritySplit, startWithNewLine, endWithNewLine, allowNewLineBefore);
        }

        public boolean keepGuidesWithoutMid() {
            return prioritySplit || startWithNewLine || endWithNewLine;
        }
    }


    public String minimal() {
        return "";
    }

    public String debug() {
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
