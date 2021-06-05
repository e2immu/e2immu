package org.e2immu.analyser.testexample;

import java.util.concurrent.atomic.AtomicInteger;

/*
 small variant on GS_4: position().msg instead of position.
 causes/caused two crashes
 */

public record GuideSimplified_5(int index, Position position) {
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

        private GuideGenerator() {
            index = generator.incrementAndGet();
        }

        public GuideSimplified_5 start() {
            return new GuideSimplified_5(index, Position.START);
        }
    }

    public String trace() {
        return "/*" + position().msg + "*/";
    }
}
