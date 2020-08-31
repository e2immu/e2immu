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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.util.StringUtil;

import java.util.Map;

public class IfElseStatement extends StatementWithExpression {
    public final Block elseBlock;
    public final Block ifBlock;

    public IfElseStatement(Expression expression,
                           Block ifBlock,
                           Block elseBlock) {
        super(expression, ForwardEvaluationInfo.NOT_NULL);
        this.ifBlock = ifBlock;
        this.elseBlock = elseBlock;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new IfElseStatement(
                translationMap.translateExpression(expression),
                translationMap.translateBlock(ifBlock),
                translationMap.translateBlock(elseBlock));
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("if (");
        sb.append(expression.expressionString(indent));
        sb.append(")");
        sb.append(ifBlock.statementString(indent));
        if (elseBlock != Block.EMPTY_BLOCK) {
            sb.append(" else");
            sb.append(elseBlock.statementString(indent));
        }
        sb.append("\n");
        return sb.toString();
    }

    // note that we add the expression only once


    @Override
    public CodeOrganization codeOrganization() {
        CodeOrganization.Builder builder = new CodeOrganization.Builder()
                .setExpression(expression)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setStatements(ifBlock)
                .setStatementsExecutedAtLeastOnce(v -> false);
        if (elseBlock != Block.EMPTY_BLOCK) {
            builder.addSubStatement(new CodeOrganization.Builder().setExpression(EmptyExpression.DEFAULT_EXPRESSION)
                    .setStatementsExecutedAtLeastOnce(v -> false)
                    .setStatements(elseBlock)
                    .build())
                    .setNoBlockMayBeExecuted(false); // either the if or the else block, but one shall be executed
        }
        return builder.build();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        SideEffect blocksSideEffect = ifBlock.sideEffect(evaluationContext);
        if (elseBlock != Block.EMPTY_BLOCK) {
            blocksSideEffect = blocksSideEffect.combine(elseBlock.sideEffect(evaluationContext));
        }
        SideEffect conditionSideEffect = expression.sideEffect(evaluationContext);
        if (blocksSideEffect == SideEffect.STATIC_ONLY && conditionSideEffect.lessThan(SideEffect.SIDE_EFFECT))
            return SideEffect.STATIC_ONLY;
        return conditionSideEffect.combine(blocksSideEffect);
    }
}
