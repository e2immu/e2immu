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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.This;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.StringUtil;

// @ContextClass
// @NullNotAllowed
// @NotNull
public class ReturnStatement extends StatementWithExpression {

    public ReturnStatement(Expression expression) {
        super(expression);
    }

    @Override
    public String statementString(int indent) {
        StringBuilder sb = new StringBuilder();
        StringUtil.indent(sb, indent);
        sb.append("return");
        if (expression != EmptyExpression.EMPTY_EXPRESSION) {
            sb.append(" ");
            sb.append(expression.expressionString(indent));
        }
        sb.append(";\n");
        return sb.toString();
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        if (expression == EmptyExpression.EMPTY_EXPRESSION || isThis() || isFirstParameter(expression)) {
            return SideEffect.STATIC_ONLY;
        }
        // at least NONE_PURE... unless the expression is tagged as "@Identity", then STATIC_ONLY is allowed
        Boolean isIdentity = isIdentity(sideEffectContext.typeContext, expression);
        if (isIdentity == null) return SideEffect.DELAYED;
        SideEffect base = isIdentity ? SideEffect.STATIC_ONLY : SideEffect.NONE_PURE;
        return base.combine(expression.sideEffect(sideEffectContext));
    }

    public Boolean isFluent(TypeContext typeContext) {
        if (expression instanceof VariableExpression) {
            VariableExpression variableExpression = (VariableExpression) expression;
            if (variableExpression.variable instanceof This) return true;
        }
        if (expression instanceof MethodCall) {
            MethodCall methodCall = (MethodCall) expression;
            return methodCall.methodInfo.isFluent(typeContext);
        }
        return false;
    }

    public static Boolean isIdentity(TypeContext typeContext, Expression expression) {
        if (isFirstParameter(expression)) return true;
        if (expression instanceof MethodCall) {
            MethodCall methodCall = (MethodCall) expression;
            if (methodCall.parameterExpressions.size() == 0) return false;
            Boolean isIdentity = methodCall.methodInfo.isIdentity(typeContext);
            if (isIdentity == Boolean.FALSE) return false;
            if (isIdentity == null) return null;
            return isIdentity(typeContext, methodCall.parameterExpressions.get(0));
        }
        return false;
    }

    private static boolean isFirstParameter(Expression expression) {
        return (expression instanceof VariableExpression) && ((VariableExpression) expression).variable instanceof ParameterInfo &&
                (((ParameterInfo) ((VariableExpression) expression).variable).index == 0);
    }

    private boolean isThis() {
        return (expression instanceof VariableExpression) && ((VariableExpression) expression).variable instanceof This;
    }
}
