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

import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Lambda;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;

public class ExpressionAsStatement extends StatementWithExpression {

    public ExpressionAsStatement(Expression expression) {
        super(createCodeOrganization(expression));
    }

    private static Structure createCodeOrganization(Expression expression) {
        Structure.Builder builder = new Structure.Builder();
        builder.setForwardEvaluationInfo(ForwardEvaluationInfo.DEFAULT);
        if (expression instanceof LocalVariableCreation) {
            builder.addInitialisers(List.of(expression));
        } else {
            builder.setExpression(expression);
        }
        if (expression instanceof Lambda) {
            builder.setBlock(((Lambda) expression).block);
        }
        return builder.build();
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ExpressionAsStatement(translationMap.translateExpression(structure.expression));
    }

    @Override
    public String statementString(int indent, NumberedStatement numberedStatement) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append(structure.expression.expressionString(indent));
        sb.append(";\n");
        return sb.toString();
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(structure.expression);
    }
}
