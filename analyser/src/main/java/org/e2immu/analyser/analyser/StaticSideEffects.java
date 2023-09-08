package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;

import java.util.List;

public record StaticSideEffects(List<Expression> expressions) {
    public CausesOfDelay causesOfDelay() {
        return expressions.stream()
                .map(Expression::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }
}
