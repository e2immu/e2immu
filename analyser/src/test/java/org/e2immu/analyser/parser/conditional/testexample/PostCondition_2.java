package org.e2immu.analyser.parser.conditional.testexample;

import java.io.IOException;

/*
this is not a post-condition!
 */
public class PostCondition_2 {

    public static void method(IOException e) {
        throw new UnsupportedOperationException("Stop: " + e.getMessage());
    }
}
