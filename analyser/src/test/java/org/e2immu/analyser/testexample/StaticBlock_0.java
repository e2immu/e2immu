package org.e2immu.analyser.testexample;

import org.e2immu.annotation.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

// bit of an overview to see where we can put static blocks

@E1Container // not @E2, because it has a modified (static) field
public class StaticBlock_0 {

    @Final
    @NotNull // no changes after construction in its own static blocks
    @Modified // other types make changes
    private static Map<String, String> map;

    static {
        map = new HashMap<>();
        map.put("1", "2");
        System.out.println("enclosing type");
    }

    @Nullable
    @NotModified
    public static String get(String s) {
        return map.get(s); // should not raise a warning!
    }

    static class SubType {
        static {
            System.out.println("sub-type");
            map.put("2", "3");
        }

        static class SubSubType {
            static {
                System.out.println("sub-sub-type");
                map.put("4", "5");
            }
        }
    }

    class SubType2 {
        static {
            System.out.println("sub-type 2");
            map.put("2", "3");
        }
    }

    @Test
    public void test() {
        System.out.println("Test!");
        SubType2 subType2 = new SubType2();
        SubType.SubSubType subSubType = new SubType.SubSubType();
        // observe that subType has not been created yet!
    }

    static {
        map.put("11", "12"); // should not raise a warning
        System.out.println("2nd part of enclosing type");
    }
}
