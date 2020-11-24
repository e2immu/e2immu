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
import org.e2immu.analyser.util.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECT;
import static org.e2immu.analyser.util.Logger.log;

public class MethodInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodInspection.class);

    private final SetOnce<MethodInspectionImpl.Builder> builderOnceFQNIsKnown = new SetOnce<>();
    private final boolean fullInspection;
    private final TypeMapImpl.Builder typeMapBuilder;
    private final String name;
    private final boolean isConstructor;
    private final TypeInfo typeInfo;

    public MethodInspector(TypeMapImpl.Builder typeMapBuilder,
                           TypeInfo typeInfo,
                           String name, boolean isConstructor, boolean fullInspection) {
        this.name = name;
        this.isConstructor = isConstructor;
        this.typeMapBuilder = typeMapBuilder;
        this.fullInspection = fullInspection;
        this.typeInfo = typeInfo;
    }

    public MethodInspectionImpl.Builder getBuilder() {
        return Objects.requireNonNull(builderOnceFQNIsKnown.get());
    }

    /*
    Inspection of a method inside an annotation type.
    Example:

    public @interface Dependent {
      boolean absent() default false; boolean contract() default false;
    }
     */

    public void inspect(AnnotationMemberDeclaration amd, ExpressionContext expressionContext) {
        log(INSPECT, "Inspecting annotation member {} in {}", name, typeInfo.fullyQualifiedName);
        MethodInspectionImpl.Builder tempBuilder = new MethodInspectionImpl.Builder(typeInfo, name);
        MethodInspectionImpl.Builder builder = fqnIsKnown(tempBuilder);

        addAnnotations(builder, amd.getAnnotations(), expressionContext);
        if (fullInspection) {
            addModifiers(builder, amd.getModifiers());
            Expression expression = expressionContext.parseExpression(amd.getDefaultValue());
            Block body = new Block.BlockBuilder().addStatement(new ReturnStatement(false, expression)).build();
            builder.setInspectedBlock(body);
            ParameterizedType returnType = ParameterizedType.from(expressionContext.typeContext, amd.getType());
            builder.setReturnType(returnType);
            typeMapBuilder.registerMethodInspection(builder);
        }
    }

    private MethodInspectionImpl.Builder fqnIsKnown(MethodInspectionImpl.Builder builder) {
        builder.readyToComputeFQN();
        log(INSPECT, "Inspecting method " + builder.getFullyQualifiedName());
        MethodInspection methodInspection = typeMapBuilder.getMethodInspectionDoNotTrigger(builder.getFullyQualifiedName());
        if (methodInspection instanceof MethodInspectionImpl.Builder existing) {
            builderOnceFQNIsKnown.set(existing);
            return existing;
        }
        if (methodInspection == null) {
            builderOnceFQNIsKnown.set(builder);
            return builder;
        }
        throw new UnsupportedOperationException();
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
        MethodInspectionImpl.Builder tempBuilder = new MethodInspectionImpl.Builder(methodInfo);
        addParameters(tempBuilder, cd.getParameters(), expressionContext, dollarResolver);
        MethodInspectionImpl.Builder builder = fqnIsKnown(tempBuilder);

        builder.addCompanionMethods(companionMethods);
        checkCompanionMethods(companionMethods);
        addAnnotations(builder, cd.getAnnotations(), expressionContext);
        if (fullInspection) {
            addModifiers(builder, cd.getModifiers());
            addExceptionTypes(builder, cd.getThrownExceptions(), expressionContext.typeContext);

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
            MethodInspectionImpl.Builder tempBuilder = new MethodInspectionImpl.Builder();

            int tpIndex = 0;
            ExpressionContext newContext = md.getTypeParameters().isEmpty() ? expressionContext :
                    expressionContext.newTypeContext("Method type parameters");
            for (com.github.javaparser.ast.type.TypeParameter typeParameter : md.getTypeParameters()) {
                TypeParameterImpl.Builder tp = new TypeParameterImpl.Builder().setName(typeParameter.getNameAsString())
                        .setIndex(tpIndex++);
                tempBuilder.addTypeParameter(tp);
                newContext.typeContext.addToContext(tp);
                tp.computeTypeBounds(newContext.typeContext, typeParameter);
            }

            addParameters(tempBuilder, md.getParameters(), newContext, dollarResolver);
            MethodInspectionImpl.Builder builder = fqnIsKnown(tempBuilder);

            builder.addCompanionMethods(companionMethods);
            checkCompanionMethods(companionMethods);

            addAnnotations(builder, md.getAnnotations(), newContext);
            if (fullInspection) {
                addModifiers(builder, md.getModifiers());
                if (isInterface) builder.addModifier(MethodModifier.PUBLIC);
                addExceptionTypes(builder, md.getThrownExceptions(), newContext.typeContext);
                ParameterizedType pt = ParameterizedType.from(newContext.typeContext, md.getType());
                builder.setReturnType(pt);

                builder.readyToComputeFQN();
                typeMapBuilder.registerMethodInspection(builder);

                if (md.getBody().isPresent()) {
                    builder.setBlock(md.getBody().get());
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("Caught exception while inspecting method {}", methodInfo.fullyQualifiedName());
            throw e;
        }
    }

    private static void addAnnotations(MethodInspectionImpl.Builder builder,
                                       NodeList<AnnotationExpr> annotations,
                                       ExpressionContext expressionContext) {
        for (AnnotationExpr ae : annotations) {
            builder.addAnnotation(AnnotationExpressionImpl.inspect(expressionContext, ae));
        }
    }

    private static void addExceptionTypes(MethodInspectionImpl.Builder builder,
                                          NodeList<ReferenceType> thrownExceptions,
                                          TypeContext typeContext) {
        for (ReferenceType referenceType : thrownExceptions) {
            ParameterizedType pt = ParameterizedType.from(typeContext, referenceType);
            builder.addExceptionType(pt);
        }
    }

    private static void addModifiers(MethodInspectionImpl.Builder builder,
                                     NodeList<Modifier> modifiers) {
        for (Modifier modifier : modifiers) {
            if (!"static".equals(modifier.getKeyword().asString()))
                builder.addModifier(MethodModifier.from(modifier));
        }
    }

    private static void addParameters(MethodInspectionImpl.Builder builder,
                                      NodeList<Parameter> parameters,
                                      ExpressionContext expressionContext,
                                      TypeInspector.DollarResolver dollarResolver) {
        int i = 0;
        for (Parameter parameter : parameters) {
            ParameterizedType pt = ParameterizedType.from(expressionContext.typeContext, parameter.getType(),
                    parameter.isVarArgs(), dollarResolver);
            ParameterInspectionImpl.Builder pib = new ParameterInspectionImpl.Builder(pt, parameter.getNameAsString(), i++);
            pib.inspect(parameter, expressionContext, parameter.isVarArgs());
            builder.addParameter(pib);
        }
    }
}
