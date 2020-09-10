package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.SetUtil;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public abstract class LoopStatement extends StatementWithExpression {
    public final String label;

    protected LoopStatement(CodeOrganization codeOrganization, String label) {
        super(codeOrganization);
        this.label = label;
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        SideEffect blocksSideEffect = codeOrganization.block.sideEffect(evaluationContext);
        SideEffect conditionSideEffect = codeOrganization.expression.sideEffect(evaluationContext);
        if (blocksSideEffect == SideEffect.STATIC_ONLY && conditionSideEffect.lessThan(SideEffect.SIDE_EFFECT))
            return SideEffect.STATIC_ONLY;
        return conditionSideEffect.combine(blocksSideEffect);
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(codeOrganization.expression, codeOrganization.block);
    }
}
