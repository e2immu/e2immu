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

package org.e2immu.analyser.parser.expr;

import com.github.javaparser.ast.expr.ArrayCreationExpr;
import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.TypeContext;

import java.util.List;
import java.util.stream.Collectors;

public class ParseArrayCreationExpr {
    public static Expression parse(ExpressionContext expressionContext, ArrayCreationExpr arrayCreationExpr) {
        ParameterizedType parameterizedType = ParameterizedType.from(expressionContext.typeContext, arrayCreationExpr.createdType());
        ArrayInitializer arrayInitializer = arrayCreationExpr.getInitializer().map(i ->
                new ArrayInitializer(expressionContext.typeContext.getPrimitives(), i.getValues().stream()
                        .map(expressionContext::parseExpression).collect(Collectors.toList()))).orElse(null);
        List<Expression> indexExpressions = arrayCreationExpr.getLevels()
                .stream().map(level -> level.getDimension().map(expressionContext::parseExpression)
                        .orElse(EmptyExpression.EMPTY_EXPRESSION)).collect(Collectors.toList());
        return new NewObject(createArrayCreationConstructor(expressionContext.typeContext, parameterizedType),
                parameterizedType, indexExpressions, arrayInitializer);
    }

    // new Type[3]; this method creates the constructor that makes this array, without attaching said constructor to the type
    static MethodInfo createArrayCreationConstructor(TypeContext typeContext, ParameterizedType parameterizedType) {
        MethodInfo constructor = new MethodInfo(parameterizedType.typeInfo, List.of());
        MethodInspection.MethodInspectionBuilder builder = new MethodInspection.MethodInspectionBuilder()
                .setBlock(Block.EMPTY_BLOCK)
                .setReturnType(parameterizedType)
                .addModifier(MethodModifier.PUBLIC);
        for (int i = 0; i < parameterizedType.arrays; i++) {
            ParameterInfo p = new ParameterInfo(constructor, typeContext.getPrimitives().intParameterizedType, "dim" + i, i);
            p.parameterInspection.set(new ParameterInspection.ParameterInspectionBuilder().setVarArgs(false).build());
            p.setAnalysis(new ParameterAnalysisImpl.Builder(typeContext.getPrimitives(), AnalysisProvider.DEFAULT_PROVIDER, p).build());
            builder.addParameter(p);
        }
        constructor.methodInspection.set(builder.build(constructor));
        constructor.setAnalysis(MethodAnalysis.createEmpty(constructor));
        return constructor;
    }

}
