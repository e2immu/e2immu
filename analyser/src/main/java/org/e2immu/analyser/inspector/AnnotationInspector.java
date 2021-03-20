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

import java.util.ArrayList;
import java.util.List;

public class AnnotationInspector {

    public static AnnotationExpression inspect(ExpressionContext expressionContext, com.github.javaparser.ast.expr.AnnotationExpr ae) {
        TypeInfo typeInfo = (TypeInfo) expressionContext.typeContext.get(ae.getNameAsString(), true);
        TypeInspection typeInspection = expressionContext.typeContext.getTypeInspection(typeInfo);

        List<Expression> analyserExpressions;
        if (ae instanceof NormalAnnotationExpr) {
            analyserExpressions = new ArrayList<>();
            for (com.github.javaparser.ast.expr.MemberValuePair mvp : ((NormalAnnotationExpr) ae).getPairs()) {
                String methodName = mvp.getNameAsString();
                ParameterizedType expectedType = expectedType(expressionContext, methodName, typeInspection);
                Expression value = expressionContext.parseExpression(mvp.getValue(), expectedType, null);
                analyserExpressions.add(new MemberValuePair(methodName, value));
            }
        } else if (ae instanceof SingleMemberAnnotationExpr) {
            ParameterizedType expectedType = expectedType(expressionContext, "value", typeInspection);
            Expression value = expressionContext.parseExpression(ae.asSingleMemberAnnotationExpr().getMemberValue(),
                    expectedType, null);
            analyserExpressions = List.of(new MemberValuePair("value", value));
        } else analyserExpressions = List.of();
        return new AnnotationExpressionImpl(typeInfo, analyserExpressions);
    }

    private static ParameterizedType expectedType(ExpressionContext expressionContext,
                                                  String methodName, TypeInspection typeInspection) {
        MethodInfo methodInfo = typeInspection.methods().stream()
                .filter(m -> m.name.equals(methodName)).findFirst().orElseThrow();
        MethodInspection methodInspection = expressionContext.typeContext.getMethodInspection(methodInfo);
        return methodInspection.getReturnType();
    }
}
