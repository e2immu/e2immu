package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.Nullable;
import org.e2immu.annotation.Variable;

import java.util.HashMap;
import java.util.Map;

// also look at the static blocks of sub-types (test @Variable instead of @Final)
// test @Nullable instead of @NotNull

public class StaticBlock_3 {

    @Variable
    @Nullable
    private static Map<String, String> map;

    static {
        map = new HashMap<>();
        map.put("1", "2"); // should not raise a warning
        System.out.println("enclosing type");
    }

    @Nullable
    @NotModified
    public static String get(String s) {
        return map.get(s); // should raise a warning!
    }

    static class SubType {
        static {
            map = null;
        }
    }
}
