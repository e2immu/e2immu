package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.SideEffectContext;

import java.util.Objects;
import java.util.function.Predicate;

public abstract class LoopStatement extends StatementWithExpression {
    public final Block block;
    public final String label;

    protected LoopStatement(String label, Expression condition, Block block) {
        super(condition, ForwardEvaluationInfo.NOT_NULL);
        this.label = label;
        this.block = Objects.requireNonNull(block);
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        SideEffect blocksSideEffect = block.sideEffect(sideEffectContext);
        SideEffect conditionSideEffect = expression.sideEffect(sideEffectContext);
        if (blocksSideEffect == SideEffect.STATIC_ONLY && conditionSideEffect.lessThan(SideEffect.SIDE_EFFECT))
            return SideEffect.STATIC_ONLY;
        return conditionSideEffect.combine(blocksSideEffect);
    }
}
