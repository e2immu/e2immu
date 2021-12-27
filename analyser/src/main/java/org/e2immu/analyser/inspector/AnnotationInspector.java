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

import java.util.ArrayList;
import java.util.List;

public class AnnotationInspector {

    public static AnnotationExpression inspect(ExpressionContext expressionContext, com.github.javaparser.ast.expr.AnnotationExpr ae) {
        TypeContext typeContext = expressionContext.typeContext();
        TypeInfo typeInfo = (TypeInfo) typeContext.get(ae.getNameAsString(), true);
        TypeInspection typeInspection = typeContext.getTypeInspection(typeInfo);

        List<Expression> analyserExpressions;
        if (ae instanceof NormalAnnotationExpr normalAnnotationExpr) {
            analyserExpressions = new ArrayList<>();
            for (com.github.javaparser.ast.expr.MemberValuePair mvp : normalAnnotationExpr.getPairs()) {
                String methodName = mvp.getNameAsString();
                ForwardReturnTypeInfo expectedType = expectedType(typeContext, methodName, typeInspection);
                Expression value;
                if (mvp.getValue().isFieldAccessExpr()) {
                    value = new UnevaluatedAnnotationParameterValue(Identifier.from(mvp), expectedType.type(),
                            mvp.getValue().asFieldAccessExpr().getNameAsString());
                } else {
                    value = expressionContext.parseExpression(mvp.getValue(), expectedType);
                }
                analyserExpressions.add(new MemberValuePair(methodName, value));
            }
        } else if (ae instanceof SingleMemberAnnotationExpr sm) {
            ForwardReturnTypeInfo expectedType = expectedType(typeContext, MemberValuePair.VALUE, typeInspection);
            com.github.javaparser.ast.expr.Expression mv = ae.asSingleMemberAnnotationExpr().getMemberValue();
            Expression value;
            if (mv.isNameExpr()) {
                value = new UnevaluatedAnnotationParameterValue(Identifier.from(mv), expectedType.type(),
                        mv.asNameExpr().getNameAsString());
            } else {
                value = expressionContext.parseExpression(mv, expectedType);
            }
            analyserExpressions = List.of(new MemberValuePair(MemberValuePair.VALUE, value));
        } else analyserExpressions = List.of();
        return new AnnotationExpressionImpl(typeInfo, analyserExpressions);
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
