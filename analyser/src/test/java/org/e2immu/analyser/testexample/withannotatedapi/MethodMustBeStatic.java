package org.e2immu.analyser.testexample.withannotatedapi;

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

    }
}
