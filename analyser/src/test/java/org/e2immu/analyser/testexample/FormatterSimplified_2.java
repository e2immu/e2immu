package org.e2immu.analyser.testexample;


import java.util.List;
import java.util.function.Function;

public class FormatterSimplified_2 {

    record ForwardInfo(int pos, int chars, String string, Split before, Guide guide, boolean symbol) {
    }

    interface OutputElement {
        default String write() {
            return "abc";
        }
    }

    record Space(Split split) implements OutputElement {
        static final Space NEWLINE = new Space(Split.NEVER);
        static final Space NONE = new Space(Split.NEVER);

        ElementarySpace elementarySpace() {
            return null;
        }
    }

    static class ElementarySpace implements OutputElement {
        static final ElementarySpace NICE = new ElementarySpace();
        static final ElementarySpace NONE = new ElementarySpace();
        static final ElementarySpace RELAXED_NONE = new ElementarySpace();
    }

    static class Split implements OutputElement {
        static final Split NEVER = new Split();
    }

    record Symbol(String symbol, Space left, Space right, String constant) implements OutputElement {
    }

    interface Guide extends OutputElement {
    }

    boolean forward(List<OutputElement> list, Function<ForwardInfo, Boolean> writer, int start, int maxChars) {
        OutputElement outputElement;
        int pos = start;
        int chars = 0;
        int end = list.size();

        ElementarySpace lastOneWasSpace = ElementarySpace.NICE; // used to avoid writing double spaces
        Split split = Split.NEVER;
        boolean wroteOnce = false; // don't write a space at the beginning of the line
        boolean allowBreak = false; // write until the first allowed space
        while (pos < end && ((outputElement = list.get(pos)) != Space.NEWLINE)) {
            String string;

            Split splitAfterWriting = Split.NEVER;
            ElementarySpace spaceAfterWriting = ElementarySpace.RELAXED_NONE;
            if (outputElement instanceof Symbol symbol) {
                split = symbol.left().split;
                lastOneWasSpace = combine(lastOneWasSpace, symbol.left().elementarySpace());
                string = symbol.symbol();
                spaceAfterWriting = symbol.right().elementarySpace();
                splitAfterWriting = symbol.right().split;
                if (split == Split.NEVER) allowBreak = false;
            } else if (outputElement instanceof Guide) {
                string = "";
            } else {
                string = outputElement.write();
            }
            // check for double spaces
            if (outputElement instanceof Space space) {
                lastOneWasSpace = combine(lastOneWasSpace, space.elementarySpace());
                allowBreak |= outputElement != Space.NONE;
            } else if (outputElement instanceof Guide guide) {
                if (chars >= maxChars) return false;
                // empty string indicates that there is a Guide on this position
                // split means nothing here
                if (writer.apply(new ForwardInfo(pos, chars, null, Split.NEVER, guide, false))) return true;
            } else if (string.length() > 0) {
                boolean writeSpace = lastOneWasSpace != ElementarySpace.NONE &&
                        lastOneWasSpace != ElementarySpace.RELAXED_NONE && wroteOnce;
                int stringLen = string.length();
                int goingToWrite = stringLen + (writeSpace ? 1 : 0);

                if (chars + goingToWrite > maxChars && allowBreak && wroteOnce) {// don't write anymore...
                    return false;
                }

                String stringToWrite = writeSpace ? (" " + string) : string;
                if (writer.apply(new ForwardInfo(pos, chars, stringToWrite, split, null,
                        outputElement instanceof Symbol))) return true;
                lastOneWasSpace = spaceAfterWriting;
                split = splitAfterWriting;
                wroteOnce = true;
                allowBreak = split != Split.NEVER;
                chars += stringLen + (writeSpace ? 1 : 0);
            }
            ++pos;
        }
        return false;
    }

    private ElementarySpace combine(ElementarySpace lastOneWasSpace, ElementarySpace elementarySpace) {
        return lastOneWasSpace == null ? elementarySpace: lastOneWasSpace;
    }
}
