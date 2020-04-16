package org.e2immu.analyser.testexample;

import java.util.stream.Stream;

public class MethodMustBeStatic {

    static class ParentClass {
        String s;

        public ParentClass(String s) {
            this.s = s;
        }
    }

    static class ChildClass extends ParentClass {

        String t;

        public ChildClass(String s, String t) {
            super(s);
            this.t = t;
        }

        public String methodMustNotBeStatic(String input) {
            return s + "something" + input;
        }

        public boolean methodMustNotBeStatic2(String input) {
            return (s instanceof String);
        }

        public static String methodMustBeStatic(String input) {
            return "something" + input;
        }

        public ChildClass methodMustNotBeStatic3(String input) {
            return this;
        }

        public String methodMustNotBeStatic4(String input) {
            return Stream.of(input).map(s -> {
                System.out.println(s);
                return s + "something" + t;
            }).findAny().get();
        }

        public ChildClass methodMustNotBeStatic5(String input) {
            return methodMustNotBeStatic3(input);
        }

    }
}
