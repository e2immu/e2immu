package org.e2immu.analyser.model;

import org.e2immu.analyser.model.variable.Variable;

public interface Visitor {

    default boolean beforeStatement(Statement statement) {
        return true; // go deeper
    }

    default void afterStatement(Statement statement) {
        // do nothing
    }

    default void startSubBlock(int n) {
        // do nothing
    }

    default void endSubBlock(int n) {
        // do nothing
    }

    default boolean beforeExpression(Expression expression) {
        return true; // go deeper
    }

    default void afterExpression(Expression expression) {
        // do nothing
    }

    default boolean beforeVariable(Variable variable) {
        return true;
    }

    default void afterVariable(Variable variable) {
        // do nothing
    }
}
