package org.e2immu.analyser.testexample;

// first variant on GS_2

public record GuideSimplified_3(int index, Position position) {

    public enum Position {
        START("S"), MID(""), END("E");

        private final String msg;

        Position(String msg) {
            this.msg = msg;
        }
    }

    public static class GuideGenerator {
        public final int index = 4;

        public GuideSimplified_3 start() {
            return new GuideSimplified_3(index, Position.START);
        }
    }

}
