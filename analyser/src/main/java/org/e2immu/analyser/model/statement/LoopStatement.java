package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.parser.SideEffectContext;

import java.util.Objects;
import java.util.function.Predicate;

public abstract class LoopStatement extends StatementWithExpression {
    public final Block block;
    public final String label;
    public final Predicate<Value> statementsExecutedAtLeastOnce;

    protected LoopStatement(String label, Expression condition, Block block, Predicate<Value> statementsExecutedAtLeastOnce) {
        super(condition);
        this.label = label;
        this.block = Objects.requireNonNull(block);
        this.statementsExecutedAtLeastOnce = statementsExecutedAtLeastOnce;
    }

    @Override
    public CodeOrganization codeOrganization() {
        return new CodeOrganization.Builder()
                .setStatementsExecutedAtLeastOnce(statementsExecutedAtLeastOnce)
                .setExpression(expression).setStatements(block).build();
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
