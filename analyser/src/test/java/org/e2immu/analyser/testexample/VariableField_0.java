package org.e2immu.analyser.testexample;

import org.e2immu.annotation.Variable;

public class VariableField_0 {

    @Variable
    private String string;

    public void setString(String string) {
        this.string = string;
    }

    public String getString(boolean b) {
        if(b) {
            if(string.startsWith("abc")) { // first copy string$0, causes CNN
                return "abc" + string; // causes new read copy string$1, but without CNN
            }
        }
        return string;
    }
}
