package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.Pair;

import java.util.List;
import java.util.Objects;

public abstract class LoopStatement extends StatementWithExpression {
    public final Block block;
    public final String label;

    protected LoopStatement(String label, Expression condition, Block block) {
        super(condition);
        this.label = label;
        this.block = Objects.requireNonNull(block);
    }

    @Override
    public CodeOrganization codeOrganization() {
        return new CodeOrganization.Builder().setExpression(expression).setStatements(block).build();
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
