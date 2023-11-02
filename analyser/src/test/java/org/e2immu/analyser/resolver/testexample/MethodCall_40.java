package org.e2immu.analyser.resolver.testexample;

import java.text.DecimalFormat;

public class MethodCall_40 {

    /*
    candidate methods have formal types double, long, Object.
    Result of valueOf is Long
     */
    public String method(String value) {
        DecimalFormat dfOID = new DecimalFormat("000000000000");
        return dfOID.format(Long.valueOf(value));
    }
}
