package org.e2immu.analyser.testexample;

import java.io.IOException;
import java.io.StringWriter;

public class Var_5 {

    public static String method(String s) {
        try (var sw = new StringWriter()) {
            return sw.append(s).toString();
        } catch (IOException ioe) {
            return "Error!";
        }
    }

}
