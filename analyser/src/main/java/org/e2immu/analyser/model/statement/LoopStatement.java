package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.*;

import java.util.List;

public abstract class LoopStatement extends StatementWithExpression {
    public final String label;

    protected LoopStatement(Structure structure, String label) {
        super(structure);
        this.label = label;
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        SideEffect blocksSideEffect = structure.block.sideEffect(evaluationContext);
        SideEffect conditionSideEffect = structure.expression.sideEffect(evaluationContext);
        if (blocksSideEffect == SideEffect.STATIC_ONLY && conditionSideEffect.lessThan(SideEffect.SIDE_EFFECT))
            return SideEffect.STATIC_ONLY;
        return conditionSideEffect.combine(blocksSideEffect);
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(structure.expression, structure.block);
    }
}
