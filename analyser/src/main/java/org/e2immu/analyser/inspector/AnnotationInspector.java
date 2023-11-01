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

package org.e2immu.analyser.inspector;

import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.expression.UnevaluatedAnnotationParameterValue;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AnnotationInspector {

    public static AnnotationExpression inspect(ExpressionContext expressionContext, com.github.javaparser.ast.expr.AnnotationExpr ae) {
        TypeContext typeContext = expressionContext.typeContext();
        TypeInfo typeInfo = (TypeInfo) typeContext.get(ae.getNameAsString(), true);
        TypeInspection typeInspection = typeContext.getTypeInspection(typeInfo);

        List<MemberValuePair> analyserExpressions;
        if (ae instanceof NormalAnnotationExpr normalAnnotationExpr) {
            analyserExpressions = new ArrayList<>();
            for (com.github.javaparser.ast.expr.MemberValuePair mvp : normalAnnotationExpr.getPairs()) {
                String methodName = mvp.getNameAsString();
                Expression value = makeValue(mvp.getValue(), expressionContext, methodName, typeInspection);
                analyserExpressions.add(new MemberValuePair(methodName, value));
            }
        } else if (ae instanceof SingleMemberAnnotationExpr sm) {
            com.github.javaparser.ast.expr.Expression mv = sm.getMemberValue();
            Expression value = makeValue(mv, expressionContext, MemberValuePair.VALUE, typeInspection);
            analyserExpressions = List.of(new MemberValuePair(MemberValuePair.VALUE, value));
        } else {
            analyserExpressions = List.of();
        }
        return new AnnotationExpressionImpl(typeInfo, analyserExpressions);
    }

    private static Expression makeValue(com.github.javaparser.ast.expr.Expression expression,
                                        ExpressionContext expressionContext,
                                        String methodName,
                                        TypeInspection typeInspection) {
        ForwardReturnTypeInfo forwardReturnTypeInfo = expectedType(expressionContext.typeContext(), methodName, typeInspection);
        AtomicBoolean containsField = new AtomicBoolean();
        expression.walk(n -> {
            if (n instanceof com.github.javaparser.ast.expr.Expression e && (e.isFieldAccessExpr() || e.isNameExpr())) {
                containsField.set(true);
            }
        });
        if (containsField.get()) {
            return new UnevaluatedAnnotationParameterValue(Identifier.from(expression), forwardReturnTypeInfo,
                    expression);
        }
        return expressionContext.parseExpression(expression, forwardReturnTypeInfo);
    }

    private static ForwardReturnTypeInfo expectedType(TypeContext typeContext,
                                                      String methodName,
                                                      TypeInspection typeInspection) {
        MethodInfo methodInfo = typeInspection.methods().stream()
                .filter(m -> m.name.equals(methodName)).findFirst().orElseThrow();
        MethodInspection methodInspection = typeContext.getMethodInspection(methodInfo);
        return new ForwardReturnTypeInfo(methodInspection.getReturnType());
    }
}
