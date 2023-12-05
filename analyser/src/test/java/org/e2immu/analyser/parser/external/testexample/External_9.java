package org.e2immu.analyser.parser.external.testexample;

import java.util.Arrays;

public class External_9 {
    public String[] s1;
    public String[] s2;

    public void negateOptions() {
        String[] tmp = s1;
        s1 = s2;
        s2 = tmp; // commenting out solves the issue
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof External_9 other)) {
            return false;
        }
        boolean e1 = Arrays.asList(s1).equals(Arrays.asList(other.s1));
        boolean e2 = Arrays.asList(s2).equals(Arrays.asList(other.s2));
        // if -> expression solves the issue
        if (e1 && e2) {
            return true;
        }
        return false;
    }

}
