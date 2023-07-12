package org.e2immu.analyser.resolver.testexample;

import java.util.HashMap;
import java.util.Map;

public class ExtendedConstructor0 {

    private Map<String, String> test() {
        return new HashMap<String, String>() {
            {
                put("x", "abc");
            }
        };

    }

    private Map<String, String> test2() {
        return new HashMap<>() {
            {
                put("y", "12345");
            }
        };
    }
}

