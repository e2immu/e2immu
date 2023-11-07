package org.e2immu.analyser.resolver.testexample;

public class MethodCall_53 {

    private interface Test {
        boolean test(char c);
    }

    public static String method1(String s) {
        return ((Test) c -> c != '%' && isPercent(c)).toString();
    }

    public static String method2(String s) {
        return (new Test() {
            @Override
            public boolean test(char c) {
                return c != '%' && isPercent(c);
            }
        }).toString();
    }

    public static String method3(String s) {
        Test test = new Test() {
            @Override
            public boolean test(char c) {
                return c != '%' && isPercent(c);
            }
        };
        return test.toString();
    }

    private static boolean isPercent(char c) {
        return c == '%';
    }

}

