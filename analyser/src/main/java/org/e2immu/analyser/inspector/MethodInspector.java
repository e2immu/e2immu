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

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ReferenceType;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECT;
import static org.e2immu.analyser.util.Logger.log;

public class MethodInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodInspection.class);

    private final MethodInfo methodInfo;
    private final MethodInspectionImpl.Builder builder;
    private final boolean fullInspection;
    private final TypeMapImpl.Builder typeMapBuilder;

    public MethodInspector(TypeMapImpl.Builder typeMapBuilder, MethodInfo methodInfo, boolean fullInspection) {
        this.methodInfo = methodInfo;
        this.typeMapBuilder = typeMapBuilder;
        MethodInspection methodInspection = typeMapBuilder.getMethodInspection(methodInfo);

        // the following statement is only correct when all imported types have been byte code analysed

        this.fullInspection = fullInspection;
        if (methodInspection == null) {
            builder = new MethodInspectionImpl.Builder(methodInfo);
        } else {
            builder = (MethodInspectionImpl.Builder) methodInspection;
        }
    }

    public MethodInspectionImpl.Builder getBuilder() {
        return Objects.requireNonNull(builder);
    }



    /*
    Inspection of a method inside an annotation type.
    Example:

    public @interface Dependent {
      boolean absent() default false; boolean contract() default false;
    }
     */

    public void inspect(AnnotationMemberDeclaration amd, ExpressionContext expressionContext) {
        log(INSPECT, "Inspecting annotation member {}", methodInfo.fullyQualifiedName());
        addAnnotations(amd.getAnnotations(), expressionContext);
        if (fullInspection) {
            addModifiers(amd.getModifiers());
            Expression expression = expressionContext.parseExpression(amd.getDefaultValue());
            Block body = new Block.BlockBuilder().addStatement(new ReturnStatement(false, expression)).build();
            builder.setInspectedBlock(body);
            ParameterizedType returnType = ParameterizedType.from(expressionContext.typeContext, amd.getType());
            builder.setReturnType(returnType);
        }
    }

    private void checkCompanionMethods(Map<CompanionMethodName, MethodInspectionImpl.Builder> companionMethods) {
        for (Map.Entry<CompanionMethodName, MethodInspectionImpl.Builder> entry : companionMethods.entrySet()) {
            CompanionMethodName companionMethodName = entry.getKey();
            MethodInspection methodInspection = entry.getValue();
            if (!methodInspection.getAnnotations().isEmpty()) {
                throw new UnsupportedOperationException("Companion methods do not accept annotations: " + companionMethodName);
            }
            if (!companionMethodName.methodName().equals(methodInfo.name)) {
                throw new UnsupportedOperationException("Companion method's name differs from the method name: " + companionMethodName + " vs " + methodInfo.name);
            }
        }
    }

    /*
    Inspection of a constructor.
    Code block will be handled later.
     */

    public void inspect(ConstructorDeclaration cd,
                        ExpressionContext expressionContext,
                        Map<CompanionMethodName, MethodInspectionImpl.Builder> companionMethods,
                        TypeInspector.DollarResolver dollarResolver) {
        log(INSPECT, "Inspecting constructor {}", methodInfo.fullyQualifiedName());
        builder.addCompanionMethods(companionMethods);
        checkCompanionMethods(companionMethods);
        addAnnotations(cd.getAnnotations(), expressionContext);
        if (fullInspection) {
            addModifiers(cd.getModifiers());
            addParameters(cd.getParameters(), expressionContext, dollarResolver);
            addExceptionTypes(cd.getThrownExceptions(), expressionContext.typeContext);

            builder.readyToComputeFQN();
            typeMapBuilder.registerMethodInspection(builder);

            builder.setBlock(cd.getBody());
        }
    }

    /*
    Inspection of a method.
    Code block will be handled later.
     */

    public void inspect(boolean isInterface,
                        MethodDeclaration md,
                        ExpressionContext expressionContext,
                        Map<CompanionMethodName, MethodInspectionImpl.Builder> companionMethods,
                        TypeInspector.DollarResolver dollarResolver) {
        try {
            log(INSPECT, "Inspecting method {}", methodInfo.fullyQualifiedName());
            builder.addCompanionMethods(companionMethods);
            checkCompanionMethods(companionMethods);

            int tpIndex = 0;
            ExpressionContext newContext = md.getTypeParameters().isEmpty() ? expressionContext :
                    expressionContext.newTypeContext("Method type parameters");
            for (com.github.javaparser.ast.type.TypeParameter typeParameter : md.getTypeParameters()) {
                org.e2immu.analyser.model.TypeParameter tp = new org.e2immu.analyser.model.TypeParameter(methodInfo,
                        typeParameter.getNameAsString(), tpIndex++);
                builder.addTypeParameter(tp);
                newContext.typeContext.addToContext(tp);
                tp.inspect(newContext.typeContext, typeParameter);
            }
            addAnnotations(md.getAnnotations(), newContext);
            if (fullInspection) {
                addModifiers(md.getModifiers());
                if (isInterface) builder.addModifier(MethodModifier.PUBLIC);
                addParameters(md.getParameters(), newContext, dollarResolver);
                addExceptionTypes(md.getThrownExceptions(), newContext.typeContext);
                ParameterizedType pt = ParameterizedType.from(newContext.typeContext, md.getType());
                builder.setReturnType(pt);

                builder.readyToComputeFQN();
                typeMapBuilder.registerMethodInspection(builder);

                if (md.getBody().isPresent()) {
                    builder.setBlock(md.getBody().get());
                }
            }
        } catch(RuntimeException e) {
            LOGGER.error("Caught exception while inspecting method {}", methodInfo.fullyQualifiedName());
            throw e;
        }
    }

    private void addAnnotations(NodeList<AnnotationExpr> annotations,
                                ExpressionContext expressionContext) {
        for (AnnotationExpr ae : annotations) {
            builder.addAnnotation(AnnotationExpressionImpl.inspect(expressionContext, ae));
        }
    }

    private void addExceptionTypes(NodeList<ReferenceType> thrownExceptions,
                                   TypeContext typeContext) {
        for (ReferenceType referenceType : thrownExceptions) {
            ParameterizedType pt = ParameterizedType.from(typeContext, referenceType);
            builder.addExceptionType(pt);
        }
    }

    private void addModifiers(NodeList<Modifier> modifiers) {
        for (Modifier modifier : modifiers) {
            if (!"static".equals(modifier.getKeyword().asString()))
                builder.addModifier(MethodModifier.from(modifier));
        }
    }

    private void addParameters(NodeList<Parameter> parameters,
                               ExpressionContext expressionContext,
                               TypeInspector.DollarResolver dollarResolver) {
        int i = 0;
        for (Parameter parameter : parameters) {
            ParameterizedType pt = ParameterizedType.from(expressionContext.typeContext, parameter.getType(), parameter.isVarArgs(), dollarResolver);
            ParameterInfo parameterInfo = new ParameterInfo(methodInfo, pt, parameter.getNameAsString(), i++);
            ParameterInspectionImpl.Builder parameterInspectionBuilder = builder.addParameterCreateBuilder(parameterInfo);
            parameterInspectionBuilder.inspect(parameter, expressionContext, parameter.isVarArgs());
        }
    }
}
