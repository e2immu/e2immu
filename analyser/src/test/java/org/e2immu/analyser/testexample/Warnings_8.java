package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Variable;


public class Warnings_8 {

    @Variable
    private String a ="abc"; // should cause warning!

    public Warnings_8(String a) {
        this.a = a;
    }

    public void setA(String a) {
        this.a = a;
    }

    public String getA() {
        return a;
    }
}
