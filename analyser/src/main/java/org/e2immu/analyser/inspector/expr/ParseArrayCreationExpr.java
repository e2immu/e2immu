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

package org.e2immu.analyser.inspector.expr;

import com.github.javaparser.ast.expr.ArrayCreationExpr;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.IntConstant;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.objectflow.ObjectFlow;

import java.util.List;
import java.util.stream.Collectors;

public class ParseArrayCreationExpr {
    public static Expression parse(ExpressionContext expressionContext, ArrayCreationExpr arrayCreationExpr) {
        ParameterizedType parameterizedType = ParameterizedTypeFactory.from(expressionContext.typeContext, arrayCreationExpr.createdType());
        ArrayInitializer arrayInitializer = arrayCreationExpr.getInitializer().map(i ->
                new ArrayInitializer(expressionContext.typeContext.getPrimitives(), ObjectFlow.NO_FLOW,
                        i.getValues().stream()
                                .map(expressionContext::parseExpression).collect(Collectors.toList()),
                        parameterizedType.copyWithOneFewerArrays())).orElse(null);
        List<Expression> indexExpressions = arrayCreationExpr.getLevels()
                .stream().map(level -> level.getDimension().map(expressionContext::parseExpression)
                        .orElse(new IntConstant(expressionContext.typeContext.getPrimitives(), 0))).collect(Collectors.toList());
        return NewObject.withArrayInitialiser(createArrayCreationConstructor(expressionContext.typeContext, parameterizedType),
                parameterizedType, indexExpressions, arrayInitializer,
                new BooleanConstant(expressionContext.typeContext.getPrimitives(), true), ObjectFlow.NO_FLOW);
    }

    // new Type[3]; this method creates the constructor that makes this array, without attaching said constructor to the type
    static MethodInfo createArrayCreationConstructor(TypeContext typeContext, ParameterizedType parameterizedType) {
        MethodInspectionImpl.Builder builder = new MethodInspectionImpl.Builder(parameterizedType.typeInfo)
                .setInspectedBlock(Block.EMPTY_BLOCK)
                .setReturnType(parameterizedType)
                .addModifier(MethodModifier.PUBLIC);
        for (int i = 0; i < parameterizedType.arrays; i++) {
            ParameterInspectionImpl.Builder p = new ParameterInspectionImpl.Builder(
                    typeContext.getPrimitives().intParameterizedType, "dim" + i, i);
            builder.addParameter(p);
        }
        MethodInfo constructor = builder.build(typeContext).getMethodInfo();
        //constructor.methodInspection.get().getParameters().forEach(p ->
        //        p.setAnalysis(new ParameterAnalysisImpl.Builder(typeContext.getPrimitives(),
        //                AnalysisProvider.DEFAULT_PROVIDER, p).build()));
        constructor.setAnalysis(MethodAnalysis.createEmpty(constructor));
        return constructor;
    }

}
