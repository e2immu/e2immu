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

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;

public class ReturnStatement extends StatementWithExpression {

    public boolean isYield;

    public ReturnStatement(boolean isYield, Expression expression) {
        super(new Structure.Builder().setExpression(expression).setForwardEvaluationInfo(ForwardEvaluationInfo.DEFAULT).build(),
                expression);
        this.isYield = isYield;
    }

    @Override
    public String statementString(int indent, StatementAnalysis statementAnalysis) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append(isYield ? "yield" : "return");
        if (expression != EmptyExpression.EMPTY_EXPRESSION) {
            sb.append(" ");
            sb.append(expression.expressionString(indent));
        }
        sb.append(";\n");
        return sb.toString();
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ReturnStatement(isYield, translationMap.translateExpression(expression));
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        if (expression == EmptyExpression.EMPTY_EXPRESSION || isThis() || isFirstParameter(expression)) {
            return SideEffect.STATIC_ONLY;
        }
        // at least NONE_PURE... unless the expression is tagged as "@Identity", then STATIC_ONLY is allowed
        int identity = identityForSideEffect(evaluationContext.getAnalyserContext(), expression);
        if (identity == Level.DELAY) return SideEffect.DELAYED;
        SideEffect base = identity == Level.TRUE ? SideEffect.STATIC_ONLY : SideEffect.NONE_PURE;
        return base.combine(expression.sideEffect(evaluationContext));
    }

    private static int identityForSideEffect(AnalysisProvider analysisProvider, Expression expression) {
        if (isFirstParameter(expression)) return Level.TRUE;
        if (expression instanceof MethodCall methodCall) {
            if (methodCall.parameterExpressions.size() == 0) return Level.FALSE;
            int identity = analysisProvider.getMethodAnalysis(methodCall.methodInfo).getProperty(VariableProperty.IDENTITY);
            if (identity != Level.TRUE) return identity;
            return identityForSideEffect(analysisProvider, methodCall.parameterExpressions.get(0));
        }
        return Level.FALSE;
    }

    private static boolean isFirstParameter(Expression expression) {
        return (expression instanceof VariableExpression) && ((VariableExpression) expression).variable instanceof ParameterInfo &&
                (((ParameterInfo) ((VariableExpression) expression).variable).index == 0);
    }

    private boolean isThis() {
        return (expression instanceof VariableExpression) &&
                ((VariableExpression) expression).variable instanceof This;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }
}
