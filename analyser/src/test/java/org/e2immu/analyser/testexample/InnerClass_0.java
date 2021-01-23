package org.e2immu.analyser.testexample;

import org.e2immu.annotation.NotNull;

public class InnerClass_0 {

    @NotNull
    private final String outerField;

    public InnerClass_0(@NotNull String outerField) {
        if(outerField == null) throw new NullPointerException();
        this.outerField = outerField;
    }

    class SubClass {
        private final String innerField; // this one should not cause an error, field is read, but outside the inner class

        SubClass(String innerField) {
            this.innerField = innerField + outerField.charAt(0);
        }

        String getOuter() {
            return InnerClass_0.this.outerField; // this tests the InnerClass.this construct
        }
    }

    public String doSomething(String input) {
        SubClass subClass = new SubClass(input);
        return outerField + " - " + subClass.innerField;
    }

    class SubClassUnusedField {
        private final String unusedInnerField; // ERROR: not used

        SubClassUnusedField(String innerField) {
            this.unusedInnerField = innerField + outerField.charAt(0);
        }
    }

    class SubClassNonPrivateNonFinalField {
        String nonPrivateNonFinal; // ERROR: not final

        public void setNonPrivateNonFinal(String nonPrivateNonFinal) {
            this.nonPrivateNonFinal = nonPrivateNonFinal;
        }

        public String getNonPrivateNonFinal() {
            return nonPrivateNonFinal;
        }
    }

    // in this nested class, we do not care about the modifier...
    private class PrivateSubClassNonPrivateNonFinalField {
        String nonPrivateNonFinalInPrivate; // no error!

        public void setNonPrivateNonFinalInPrivate(String nonPrivateNonFinalInPrivate) {
            this.nonPrivateNonFinalInPrivate = nonPrivateNonFinalInPrivate;
        }

        public String getNonPrivateNonFinalInPrivate() {
            return nonPrivateNonFinalInPrivate;
        }
    }

    static class SubClassAssignmentFromEnclosing {
        private String willBeAssignedFromOutside;

        public void setWillBeAssignedFromOutside(String willBeAssignedFromOutside) {
            this.willBeAssignedFromOutside = willBeAssignedFromOutside;
        }

        public String getWillBeAssignedFromOutside() {
            return willBeAssignedFromOutside;
        }
    }

    // ERROR method should be marked static
    public void doAssignmentIntoNestedType(String s) {
        SubClassAssignmentFromEnclosing a = new SubClassAssignmentFromEnclosing();
        a.willBeAssignedFromOutside = s; // WARN not allowed to assign
    }
}