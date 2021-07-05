package org.e2immu.analyser.testexample;


import java.util.List;
import java.util.function.Function;

// simpler version of 2, other problem in if sequence
public class FormatterSimplified_4 {

    interface OutputElement {
    }

    record Space() implements OutputElement {
        static final Space NEWLINE = new Space();

        ElementarySpace elementarySpace() {
            return null;
        }
    }

    static class ElementarySpace implements OutputElement {
        static final ElementarySpace NICE = new ElementarySpace();
    }

    record Symbol(String symbol, Space left, Space right, String constant) implements OutputElement {
    }

    boolean forward(List<OutputElement> list) {
        OutputElement outputElement;
        int pos = 0;
        int end = list.size();

        ElementarySpace lastOneWasSpace = ElementarySpace.NICE; // used to avoid writing double spaces
        while (pos < end && ((outputElement = list.get(pos)) != Space.NEWLINE)) {
            if (outputElement instanceof Symbol symbol) {
                lastOneWasSpace = combine(lastOneWasSpace, symbol.left().elementarySpace());
            } // $4$4.0.0:M
            if (outputElement instanceof Space space) {
                lastOneWasSpace = combine(lastOneWasSpace, space.elementarySpace()); // here we access $4$4.0.0:M
            }
            ++pos;
        }
        return false;
    }

    private ElementarySpace combine(ElementarySpace lastOneWasSpace, ElementarySpace elementarySpace) {
        return lastOneWasSpace == null ? elementarySpace: lastOneWasSpace;
    }
}
