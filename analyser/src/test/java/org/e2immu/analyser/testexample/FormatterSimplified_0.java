package org.e2immu.analyser.testexample;

import org.e2immu.analyser.output.*;

// https://github.com/javaparser/javaparser/issues/3260

public record FormatterSimplified_0(int options) {

    record ForwardInfo(int chars, String string) {
        public boolean isGuide() {
            return string == null;
        }

        public int charsPlusString() {
            return chars + (string == null ? 0 : string.length());
        }
    }
}