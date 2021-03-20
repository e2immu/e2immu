/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.List;

public class ReturnStatement extends StatementWithExpression {

    public ReturnStatement(Expression expression) {
        super(new Structure.Builder().setExpression(expression).setForwardEvaluationInfo(ForwardEvaluationInfo.DEFAULT).build(),
                expression);
    }

    @Override
    public OutputBuilder output(Qualification qualification, StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("return"));
        if (expression != EmptyExpression.EMPTY_EXPRESSION) {
            outputBuilder.add(Space.ONE).add(expression.output(qualification));
        }
        outputBuilder.add(Symbol.SEMICOLON).addIfNotNull(messageComment(statementAnalysis));
        return outputBuilder;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ReturnStatement(translationMap.translateExpression(expression));
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
        return expression instanceof VariableExpression ve && ve.variable() instanceof ParameterInfo pi &&
                pi.index == 0;
    }

    private boolean isThis() {
        return expression instanceof VariableExpression ve && ve.variable() instanceof This;
    }

    @Override
    public List<? extends Element> subElements() {
        return List.of(expression);
    }
}
