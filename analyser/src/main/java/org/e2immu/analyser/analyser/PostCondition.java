package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.DelayedExpression;
import org.e2immu.analyser.parser.Primitives;

/**
 * A pre-condition is a condition that is computed using non-modifying statements only, before
 * any modification has taken place.
 * Any other escape from the method (assert, throws, ...) is recorded as a post-condition.
 * Post-conditions must, by this definition, be associated with one or more modifications.
 * The highest such modification is recorded.
 *
 * @param expression
 * @param index
 */
public record PostCondition(Expression expression, String index) {
    public static final PostCondition NO_INFO_YET = new PostCondition(DelayedExpression.NO_POST_CONDITION_INFO,
            VariableInfoContainer.NOT_YET_READ);

    public static PostCondition empty(Primitives primitives) {
        return new PostCondition(new BooleanConstant(primitives, true), VariableInfoContainer.NOT_YET_READ);
    }

    public boolean isNotEmpty() {
        return !expression.isBoolValueTrue();
    }

    public CausesOfDelay causesOfDelay() {
        return expression.causesOfDelay();
    }

    public boolean isNoInformationYet() {
        return this == NO_INFO_YET;
    }
}
