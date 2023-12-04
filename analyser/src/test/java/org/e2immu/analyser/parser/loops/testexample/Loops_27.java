package org.e2immu.analyser.parser.loops.testexample;

class Loops_27 {

   static int method(String b) {
        int i = 0;
        for (; ; i++) {
            char cb = b.charAt(i);
            if (!Character.isDigit(cb)) {
                return i+2;
            }
        }
    }
}
