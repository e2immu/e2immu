package org.e2immu.analyser.parser.external.testexample;

public class External_15 {

    private String s;

    public External_15() {
        s = "";
    }

    public External_15 copy() {
        External_15 copy = new External_15();
        copy.s = this.s;
        return copy;
    }

    public String getS() {
        return s;
    }

    public void setS(String s) {
        this.s = s;
    }
}
