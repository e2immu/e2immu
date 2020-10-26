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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.Set;

public class ForEachStatement extends LoopStatement {
    public ForEachStatement(String label,
                            LocalVariable localVariable,
                            Expression expression,
                            Block block) {
        super(new Structure.Builder()
                .setStatementsExecutedAtLeastOnce((v, evaluationContext) -> evaluationContext.getProperty(v, VariableProperty.SIZE) >= Level.SIZE_NOT_EMPTY)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setLocalVariableCreation(localVariable)
                .setExpression(expression)
                .setBlock(block).build(), label);
    }


    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ForEachStatement(label,
                translationMap.translateLocalVariable(structure.localVariableCreation),
                translationMap.translateExpression(expression),
                translationMap.translateBlock(structure.block));
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(expression.typesReferenced(),
                structure.block.typesReferenced(),
                structure.localVariableCreation.parameterizedType.typesReferenced(true));
    }

    @Override
    public String statementString(int indent, StatementAnalysis statementAnalysis) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        if (label != null) {
            sb.append(label).append(": ");
        }
        sb.append("for (");
        sb.append(structure.localVariableCreation.parameterizedType.stream());
        sb.append(" ");
        sb.append(structure.localVariableCreation.name);
        sb.append(" : ");
        sb.append(expression.expressionString(indent));
        sb.append(")");
        sb.append(structure.block.statementString(indent, StatementAnalysis.startOfBlock(statementAnalysis, 0)));
        sb.append("\n");
        return sb.toString();
    }
}
