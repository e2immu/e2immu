package org.e2immu.analyser.testexample;


import java.util.concurrent.atomic.AtomicInteger;

public record GuideSimplified_2(int index,
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

        public GuideSimplified_2 start() {
            return new GuideSimplified_2(index, Position.START, tabs, prioritySplit, startWithNewLine, endWithNewLine, allowNewLineBefore);
        }

        public GuideSimplified_2 mid() {
            return new GuideSimplified_2(index, Position.MID, tabs, prioritySplit, startWithNewLine, endWithNewLine, allowNewLineBefore);
        }

        public GuideSimplified_2 end() {
            return new GuideSimplified_2(index, Position.END, tabs, prioritySplit, startWithNewLine, endWithNewLine, allowNewLineBefore);
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
