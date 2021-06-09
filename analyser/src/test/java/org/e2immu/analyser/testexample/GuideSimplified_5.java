package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/*
 small variant on GS_4: position().msg instead of position.
 causes/caused a number of crashes
 */

public record GuideSimplified_5(int index, Position position) {
    private static final AtomicInteger generator = new AtomicInteger();

    public GuideSimplified_5 {
        assert position != null;
    }

    public enum Position {
        START("S"), MID(""), END("E");

        @Nullable
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
