package org.e2immu.analyser.parser.functional.testexample;

import java.util.List;
import java.util.stream.Stream;

public class Lambda_18 {

    public static void method1() {
        Runnable finallyMethod = () -> System.out.println("this was method1");
        finallyMethod.run();
    }

    public static String method2(String in) {
        Runnable finallyMethod = () -> System.out.println("this was "+in);
        finallyMethod.run();
        return in;
    }
}
