package org.e2immu.analyser.parser.start.testexample;

import java.util.HashSet;
import java.util.Set;

public class Mutable_2 {

    private final Set<String> set = new HashSet<>();

    public int method(String s) {
        if (set.contains(s)) {
            return s.length();
        }
        set.add(s);
        /*
         core of the test: the return value "set.contains(s)?s.length():-1"
         should not evaluate to s.length() because of the value for set:
         "instance type HashSet<String> *this.contains(s)&&this.size()>=1*"

         20230912: test not that relevant, companions disabled
         */
        return -1;
    }

}
