package org.e2immu.analyser.model.impl;

import org.e2immu.analyser.model.Comment;

public record UntypedComment(String text) implements Comment {

    public static class Builder {
        private final StringBuilder stringBuilder = new StringBuilder();

        public Builder add(String string) {
            if (!stringBuilder.isEmpty()) {
                stringBuilder.append('\n');
            }
            stringBuilder.append(string.trim());
            return this;
        }

        public UntypedComment build() {
            return new UntypedComment(stringBuilder.toString());
        }
    }
}
