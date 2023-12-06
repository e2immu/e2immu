package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;

import java.util.List;

public record StaticSideEffects(List<Expression> expressions, CausesOfDelay causesOfDelay) {
}
