package org.e2immu.analyser.testexample;


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
        // FIXME remove the reference to Position.START, and things work again :-(
        // does work: make position into a parameter
        // does work: make Position into a (static) final field
        // it still fails with the indirection of a local variable
        public GuideSimplified_3 start() {
            return new GuideSimplified_3(index, Position.START);
        }
    }

}
