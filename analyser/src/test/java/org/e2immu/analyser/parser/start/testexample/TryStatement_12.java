package org.e2immu.analyser.parser.start.testexample;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;

public class TryStatement_12 {

    public static String same1(String in1, String in2) {
        try (BufferedReader br1 = new BufferedReader(new StringReader(in1));
             BufferedReader br2 = new BufferedReader(new StringReader(in2))) {
            return br1.readLine() + "=" + br2.readLine();
        } catch (IOException ioe) {
            System.err.println("Cannot read!");
            throw new UnsupportedOperationException("stop: " + ioe.getMessage());
        } finally {
            System.out.println("done");
            System.gc();
        }
    }

}
