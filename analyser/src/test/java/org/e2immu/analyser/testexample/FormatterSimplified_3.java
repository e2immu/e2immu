package org.e2immu.analyser.testexample;


import java.util.List;

// trimmed down version of 2

public class FormatterSimplified_3 {

    interface OutputElement {
        default String write() {
            return "abc";
        }
    }

    record Symbol(String symbol, String constant) implements OutputElement {
    }

    interface Guide extends OutputElement {
    }

    static boolean forward(List<OutputElement> list, int start) {
        OutputElement outputElement = list.get(start);

        if (outputElement instanceof Symbol symbol) {
            String s = symbol.toString();
        } else if (outputElement instanceof Guide) {
            String string = "";
        }

        return false;
    }
}
