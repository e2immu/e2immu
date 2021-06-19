package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Final;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;

import java.util.HashMap;
import java.util.Map;

// also look at the static blocks of sub-types; test @Modified instead of @NotModified

public class StaticBlock_4 {

    @Final
    @NotNull
    @Modified
    private static Map<String, String> map;

    static {
        map = new HashMap<>();
        map.put("1", "2"); // should not raise a warning
        System.out.println("enclosing type");
    }

    static class SubType {
        static {
            map.put("3", "4");
        }
    }
}
