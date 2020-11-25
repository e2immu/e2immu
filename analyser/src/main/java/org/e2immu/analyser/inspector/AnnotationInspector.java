/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.inspector;

import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr;
import org.e2immu.analyser.model.AnnotationExpression;
import org.e2immu.analyser.model.AnnotationExpressionImpl;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.MemberValuePair;

import java.util.ArrayList;
import java.util.List;

public class AnnotationInspector {

    public static AnnotationExpression inspect(ExpressionContext expressionContext, com.github.javaparser.ast.expr.AnnotationExpr ae) {
        List<Expression> analyserExpressions;
        if (ae instanceof NormalAnnotationExpr) {
            analyserExpressions = new ArrayList<>();
            for (com.github.javaparser.ast.expr.MemberValuePair mvp : ((NormalAnnotationExpr) ae).getPairs()) {
                Expression value = expressionContext.parseExpression(mvp.getValue());
                analyserExpressions.add(new MemberValuePair(mvp.getName().asString(), value));
            }
        } else if (ae instanceof SingleMemberAnnotationExpr) {
            Expression value = expressionContext.parseExpression(ae.asSingleMemberAnnotationExpr().getMemberValue());
            analyserExpressions = List.of(new MemberValuePair("value", value));
        } else analyserExpressions = List.of();
        TypeInfo typeInfo = (TypeInfo) expressionContext.typeContext.get(ae.getNameAsString(), true);
        return new AnnotationExpressionImpl(typeInfo, analyserExpressions);
    }

}
