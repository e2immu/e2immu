/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.FlowData;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;

import static org.e2immu.analyser.analyser.StatementAnalysis.startOfBlock;

public class IfElseStatement extends StatementWithExpression {
    public final Block elseBlock;

    public IfElseStatement(Expression expression,
                           Block ifBlock,
                           Block elseBlock) {
        super(createCodeOrganization(expression, ifBlock, elseBlock), expression);
        this.elseBlock = elseBlock;
    }

    // note that we add the expression only once
    private static Structure createCodeOrganization(Expression expression, Block ifBlock, Block elseBlock) {
        Structure.Builder builder = new Structure.Builder()
                .setExpression(expression)
                .setExpressionIsCondition(true)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setBlock(ifBlock)
                .setStatementExecution((v, ec) -> standardExecution(v));
        if (elseBlock != Block.EMPTY_BLOCK) {
            builder.addSubStatement(new Structure.Builder().setExpression(EmptyExpression.DEFAULT_EXPRESSION)
                    .setStatementExecution(StatementExecution.DEFAULT)
                    .setBlock(elseBlock)
                    .build());
        }
        return builder.build();
    }

    private static FlowData.Execution standardExecution(Expression v) {
        if (v.isBoolValueTrue()) return FlowData.Execution.ALWAYS;
        if (v.isBoolValueFalse()) return FlowData.Execution.NEVER;
        return FlowData.Execution.CONDITIONALLY;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new IfElseStatement(
                translationMap.translateExpression(expression),
                translationMap.translateBlock(structure.block),
                translationMap.translateBlock(elseBlock));
    }

    @Override
    public String statementString(int indent, StatementAnalysis statementAnalysis) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("if (");
        sb.append(expression.expressionString(indent));
        sb.append(")");
        sb.append(structure.block.statementString(indent, startOfBlock(statementAnalysis, 0)));
        if (elseBlock != Block.EMPTY_BLOCK) {
            sb.append(" else");
            sb.append(elseBlock.statementString(indent, startOfBlock(statementAnalysis, 1)));
        }
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        SideEffect blocksSideEffect = structure.block.sideEffect(evaluationContext);
        if (elseBlock != Block.EMPTY_BLOCK) {
            blocksSideEffect = blocksSideEffect.combine(elseBlock.sideEffect(evaluationContext));
        }
        SideEffect conditionSideEffect = expression.sideEffect(evaluationContext);
        if (blocksSideEffect == SideEffect.STATIC_ONLY && conditionSideEffect.lessThan(SideEffect.SIDE_EFFECT))
            return SideEffect.STATIC_ONLY;
        return conditionSideEffect.combine(blocksSideEffect);
    }

    @Override
    public List<? extends Element> subElements() {
        if (elseBlock == Block.EMPTY_BLOCK) {
            return List.of(expression, structure.block);
        }
        return List.of(expression, structure.block, elseBlock);
    }
}
