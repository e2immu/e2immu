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
import org.e2immu.analyser.util.StringUtil;

import java.util.Map;
import java.util.Objects;

public class SynchronizedStatement extends StatementWithExpression {
    public final Block block;

    public SynchronizedStatement(Expression expression,
                                 Block block) {
        super(expression, ForwardEvaluationInfo.NOT_NULL);
        Objects.requireNonNull(block);
        // elseBlock may be absent
        this.block = block;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new SynchronizedStatement(translationMap.translateExpression(expression),
                translationMap.translateBlock(block));
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("synchronized (");
        sb.append(expression.expressionString(indent));
        sb.append(")");
        sb.append(block.statementString(indent));
        sb.append("\n");
        return sb.toString();
    }

    @Override
    public CodeOrganization codeOrganization() {
        return new CodeOrganization.Builder()
                .setExpression(expression)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setStatementsExecutedAtLeastOnce(v -> true)
                .setNoBlockMayBeExecuted(false)
                .setStatements(block).build();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return block.sideEffect(evaluationContext);
    }
}
