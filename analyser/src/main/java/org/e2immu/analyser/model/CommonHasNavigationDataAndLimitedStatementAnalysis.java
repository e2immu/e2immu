package org.e2immu.analyser.model;

import org.e2immu.annotation.NotNull;

public interface CommonHasNavigationDataAndLimitedStatementAnalysis {
    @NotNull
    default String index() {
        throw new UnsupportedOperationException();
    }

    @NotNull
    default Statement statement() {
        throw new UnsupportedOperationException();
    }
}
