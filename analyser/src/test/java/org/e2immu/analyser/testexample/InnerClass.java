package org.e2immu.analyser.testexample;

public class InnerClass {

    private final String outerField;

    public InnerClass(String outerField) {
        this.outerField = outerField;
    }

    class SubClass {
        private final String innerField;

        SubClass(String innerField) {
            this.innerField = innerField + outerField.charAt(0);
        }

        String getOuter() {
            return InnerClass.this.outerField; // this tests the InnerClass.this construct
        }
    }

    public String doSomething(String input) {
        SubClass subClass = new SubClass(input);
        return outerField + " - " + subClass.innerField;
    }
}
