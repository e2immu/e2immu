package org.e2immu.analyser.testexample;

import java.util.concurrent.atomic.AtomicInteger;

/*
 second variant on GS_2

 - remove either start() or trace(), and all is fine.
  (so removing start with position.msg is still OK)
 - remove .msg in trace, and all is fine as well
 */

public record GuideSimplified_4(int index, Position position) {
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

        public GuideSimplified_4 start() {
            return new GuideSimplified_4(index, Position.START);
        }
    }

    public String trace() {
        return "/*" + position.msg + "*/";
    }
}
