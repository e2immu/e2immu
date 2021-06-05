package org.e2immu.analyser.testexample;

public class Enum_8 {
    public enum Position {
        START("S"), MID(""), END("E");

        private final String msg;

        Position(String msg) {
            this.msg = msg;
        }
    }

    public static String getPositionMessage(int i) {
        return i==0 ? Position.START.msg: Position.END.msg;
    }
}
