package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.parser.SideEffectContext;

import java.util.List;
import java.util.Objects;

public abstract class LoopStatement extends StatementWithExpression {
    public final Block block;

    protected LoopStatement(Expression condition, Block block) {
        super(condition);
        this.block = Objects.requireNonNull(block);
    }

    @Override
    public List<Block> blocks() {
        return List.of(block);
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        SideEffect blocksSideEffect = blocks().stream()
                .map(s -> s.sideEffect(sideEffectContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);
        SideEffect conditionSideEffect = expression.sideEffect(sideEffectContext);
        if (blocksSideEffect == SideEffect.STATIC_ONLY && conditionSideEffect.lessThan(SideEffect.SIDE_EFFECT))
            return SideEffect.STATIC_ONLY;
        return conditionSideEffect.combine(blocksSideEffect);
    }
}
