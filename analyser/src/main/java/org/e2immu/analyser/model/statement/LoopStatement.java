package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.SetUtil;

import java.util.Objects;
import java.util.Set;

public abstract class LoopStatement extends StatementWithExpression {
    public final Block block;
    public final String label;

    protected LoopStatement(String label, Expression condition, Block block) {
        super(condition, ForwardEvaluationInfo.NOT_NULL);
        this.label = label;
        this.block = Objects.requireNonNull(block);
    }

    @Override
    public Set<TypeInfo> typesReferenced() {
        return SetUtil.immutableUnion(super.typesReferenced(), block.typesReferenced());
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        SideEffect blocksSideEffect = block.sideEffect(evaluationContext);
        SideEffect conditionSideEffect = expression.sideEffect(evaluationContext);
        if (blocksSideEffect == SideEffect.STATIC_ONLY && conditionSideEffect.lessThan(SideEffect.SIDE_EFFECT))
            return SideEffect.STATIC_ONLY;
        return conditionSideEffect.combine(blocksSideEffect);
    }
}
