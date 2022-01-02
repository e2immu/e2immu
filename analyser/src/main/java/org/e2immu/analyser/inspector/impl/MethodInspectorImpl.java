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

package org.e2immu.analyser.inspector.impl;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ReferenceType;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.TypeMap;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.support.SetOnce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECTOR;
import static org.e2immu.analyser.util.Logger.log;

public class MethodInspectorImpl implements MethodInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodInspection.class);

    private final SetOnce<MethodInspection.Builder> builderOnceFQNIsKnown = new SetOnce<>();
    private final boolean fullInspection;
    private final TypeMap.Builder typeMapBuilder;
    private final TypeInfo typeInfo;

    public MethodInspectorImpl(TypeMap.Builder typeMapBuilder, TypeInfo typeInfo, boolean fullInspection) {
        this.typeMapBuilder = typeMapBuilder;
        this.fullInspection = fullInspection;
        this.typeInfo = typeInfo;
    }

    @Override
    public MethodInspection.Builder getBuilder() {
        return Objects.requireNonNull(builderOnceFQNIsKnown.get());
    }

    /*
    Inspection of a method inside an annotation type.
    Example:

    public @interface Dependent {
      boolean absent() default false; boolean contract() default false;
    }
     */

    @Override
    public void inspect(AnnotationMemberDeclaration amd, ExpressionContext expressionContext) {
        String name = amd.getNameAsString();
        log(INSPECTOR, "Inspecting annotation member {} in {}", name, typeInfo.fullyQualifiedName);
        MethodInspection.Builder tempBuilder = new MethodInspectionImpl.Builder(Identifier.from(amd), typeInfo, name);
        MethodInspection.Builder builder = fqnIsKnown(expressionContext.typeContext(), tempBuilder, false);
        assert builder != null;

        addAnnotations(builder, amd.getAnnotations(), expressionContext);
        if (fullInspection) {
            addModifiers(builder, amd.getModifiers());
            Expression expression = amd.getDefaultValue().map(expressionContext::parseExpressionStartVoid).orElse(EmptyExpression.EMPTY_EXPRESSION);
            Block body = new Block.BlockBuilder(Identifier.generate())
                    .addStatement(new ReturnStatement(Identifier.generate(), expression)).build();
            builder.setInspectedBlock(body);
            ParameterizedType returnType = ParameterizedTypeFactory.from(expressionContext.typeContext(), amd.getType());
            builder.setReturnType(returnType);
            typeMapBuilder.registerMethodInspection(builder);
        }
    }

    private MethodInspection.Builder fqnIsKnown(InspectionProvider inspectionProvider,
                                                MethodInspection.Builder builder,
                                                boolean returnNullWhenExistsAndFullInspection) {
        builder.readyToComputeFQN(inspectionProvider);
        String distinguishingName = builder.getDistinguishingName();
        MethodInspection methodInspection = typeMapBuilder.getMethodInspectionDoNotTrigger(distinguishingName);
        if (methodInspection instanceof MethodInspection.Builder existing) {
            if (fullInspection && returnNullWhenExistsAndFullInspection) return null;
            assert !fullInspection;
            log(INSPECTOR, "Inspecting method {}, already byte-code inspected", distinguishingName);

            builderOnceFQNIsKnown.set(existing);
            return existing;
        }
        if (methodInspection == null) {
            if (fullInspection) {
                log(INSPECTOR, "Inspecting method {}, full inspection", distinguishingName);
                builderOnceFQNIsKnown.set(builder);
                return builder;
            }
            // not a full inspection, method does not exist. we make a copy, IF the method exists in one of the direct
            // super types. See JavaUtil, forEach in Collection, with different annotations than forEach in Iterable

            MethodInspection parent = findInSuperType(inspectionProvider, builder);
            if (parent != null) {
                log(INSPECTOR, "Create method {} as copy from super type, shallow inspection", distinguishingName);
                builder.copyFrom(parent);
                builderOnceFQNIsKnown.set(builder);
                typeMapBuilder.registerMethodInspection(builder);
                return builder;
            }
            throw new UnsupportedOperationException("Cannot find method " + distinguishingName);
        }
        throw new UnsupportedOperationException();
    }

    static MethodInspection findInSuperType(InspectionProvider inspectionProvider, MethodInspection.Builder builder) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(builder.owner());
        if (typeInspection.parentClass() != null) {
            MethodInspection parent = findMethodInSuperType(inspectionProvider, builder, typeInspection.parentClass());
            if (parent != null) return parent;
        }
        for (ParameterizedType interfaceImplemented : typeInspection.interfacesImplemented()) {
            MethodInspection fromSuper = findMethodInSuperType(inspectionProvider, builder, interfaceImplemented);
            if (fromSuper != null) return fromSuper;
        }
        return null;
    }

    static MethodInspection findMethodInSuperType(InspectionProvider inspectionProvider,
                                                  MethodInspection.Builder builder,
                                                  ParameterizedType superTypeDefinition) {
        TypeInspection typeInspection = inspectionProvider.getTypeInspection(superTypeDefinition.typeInfo);
        Optional<MethodInfo> candidate;
        if (builder.isConstructor()) {
            candidate = typeInspection.constructors().stream()
                    .filter(c -> identicalParameterLists(inspectionProvider, c, builder, superTypeDefinition)).findFirst();
        } else {
            candidate = typeInspection.methods().stream().filter(m -> m.name.equals(builder.name()))
                    .filter(c -> identicalParameterLists(inspectionProvider, c, builder, superTypeDefinition)).findFirst();
        }
        return candidate.map(inspectionProvider::getMethodInspection).orElse(null);
    }

    static boolean identicalParameterLists(InspectionProvider inspectionProvider,
                                           MethodInfo candidate,
                                           MethodInspection.Builder me,
                                           ParameterizedType superTypeDefinition) {
        MethodInspection candidateInspection = inspectionProvider.getMethodInspection(candidate);
        if (candidateInspection.getParameters().size() != me.getParameters().size()) return false;
        return ListUtil.joinLists(candidateInspection.getParameters(), me.getParameters()).allMatch(pair ->
                sameType(pair.k.parameterizedType, pair.v.parameterizedType, superTypeDefinition));
    }

    /*
    forEach(Consumer<? super E>) in Collection -- forEach(Consumer<? super E>) in Iterable, knowing that Collection<E> implements Iterable<E>
    superTypeDefinition is Iterable<E>, so we know that E in Collection maps to the 1st type parameter of Iterable
     */
    static boolean sameType(ParameterizedType ptSub, ParameterizedType ptSuper, ParameterizedType superTypeDefinition) {
        if (ptSub.isType() && ptSuper.isType()) {
            return ptSub.typeInfo == ptSuper.typeInfo;
        }
        return true; // FIXME need more code here! and this probably exists somewhere? YES in ShallowMethodResolver
    }

    private void checkCompanionMethods(Map<CompanionMethodName, MethodInspection.Builder> companionMethods, String mainMethodName) {
        for (Map.Entry<CompanionMethodName, MethodInspection.Builder> entry : companionMethods.entrySet()) {
            CompanionMethodName companionMethodName = entry.getKey();
            MethodInspection.Builder companionInspection = entry.getValue();
            if (!companionInspection.getAnnotations().isEmpty()) {
                throw new UnsupportedOperationException("Companion methods do not accept annotations: " + companionMethodName);
            }
            if (!companionMethodName.methodName().equals(mainMethodName)) {
                throw new UnsupportedOperationException("Companion method's name differs from the method name: " +
                        companionMethodName + " vs " + mainMethodName);
            }
        }
    }

    /*
    Compact constructor for records
     */
    @Override
    public boolean inspect(CompactConstructorDeclaration ccd,
                           ExpressionContext expressionContext,
                           Map<CompanionMethodName, MethodInspection.Builder> companionMethods,
                           List<RecordField> fields) {
        Identifier identifier = ccd == null ? typeInfo.identifier : Identifier.from(ccd);
        MethodInspection.Builder tempBuilder = MethodInspectionImpl.Builder.compactConstructor(identifier, typeInfo);
        int i = 0;
        for (RecordField recordField : fields) {
            ParameterInspection.Builder pib = new ParameterInspectionImpl.Builder(Identifier.generate(),
                    recordField.fieldInfo().type, recordField.fieldInfo().name, i++);
            pib.setVarArgs(recordField.varargs());
            tempBuilder.addParameter(pib);
        }
        MethodInspection.Builder builder = fqnIsKnown(expressionContext.typeContext(), tempBuilder,
                ccd == null);
        if (builder == null) {
            LOGGER.debug("Nothing to be done; there is no need for this compact constructor");
            return false;
        }
        builder.addModifier(MethodModifier.PUBLIC);
        if (ccd != null) {
            builder.addCompanionMethods(companionMethods);
            checkCompanionMethods(companionMethods, typeInfo.simpleName);
            addAnnotations(builder, ccd.getAnnotations(), expressionContext);
            if (fullInspection) {
                addModifiers(builder, ccd.getModifiers());
                addExceptionTypes(builder, ccd.getThrownExceptions(), expressionContext.typeContext());
                builder.setBlock(ccd.getBody());
            }
        } else {
            builder.setSynthetic(true);
        }
        typeMapBuilder.registerMethodInspection(builder);
        return true;
    }

    /*
    Inspection of a static block
     */
    @Override
    public void inspect(InitializerDeclaration id,
                        ExpressionContext expressionContext,
                        int staticBlockIdentifier) {
        MethodInspection.Builder tempBuilder = MethodInspectionImpl.Builder.createStaticBlock(
                Identifier.from(id), typeInfo, staticBlockIdentifier);
        MethodInspection.Builder builder = fqnIsKnown(expressionContext.typeContext(), tempBuilder,
                false);
        assert fullInspection : "? otherwise we would not see them";
        assert builder != null;
        typeMapBuilder.registerMethodInspection(builder);
        builder.setBlock(id.getBody());
    }

    /*
    Inspection of a constructor.
    Code block will be handled later.
     */
    @Override
    public void inspect(ConstructorDeclaration cd,
                        ExpressionContext expressionContext,
                        Map<CompanionMethodName, MethodInspection.Builder> companionMethods,
                        DollarResolver dollarResolver,
                        boolean makePrivate) {
        MethodInspection.Builder tempBuilder = new MethodInspectionImpl.Builder(Identifier.from(cd), typeInfo);
        ExpressionContext newContext = addTypeParameters(cd, expressionContext, tempBuilder);

        addParameters(tempBuilder, cd.getParameters(), newContext, dollarResolver);
        MethodInspection.Builder builder = fqnIsKnown(newContext.typeContext(), tempBuilder, false);
        assert builder != null;
        inspectParameters(cd.getParameters(), builder.getParameterBuilders(), newContext);
        if (makePrivate) {
            builder.addModifier(MethodModifier.PRIVATE);
        }
        builder.addCompanionMethods(companionMethods);
        checkCompanionMethods(companionMethods, typeInfo.simpleName);
        addAnnotations(builder, cd.getAnnotations(), newContext);
        if (fullInspection) {
            addModifiers(builder, cd.getModifiers());
            addExceptionTypes(builder, cd.getThrownExceptions(), newContext.typeContext());

            typeMapBuilder.registerMethodInspection(builder);

            builder.setBlock(cd.getBody());
        }
    }

    /*
    Inspection of a method.
    Code block will be handled later.
     */

    @Override
    public void inspect(boolean isInterface,
                        String methodName,
                        MethodDeclaration md,
                        ExpressionContext expressionContext,
                        Map<CompanionMethodName, MethodInspection.Builder> companionMethods,
                        DollarResolver dollarResolver) {
        try {
            MethodInspectionImpl.Builder tempBuilder = new MethodInspectionImpl
                    .Builder(Identifier.from(md), typeInfo, methodName);

            ExpressionContext newContext = addTypeParameters(md, expressionContext, tempBuilder);

            addParameters(tempBuilder, md.getParameters(), newContext, dollarResolver);
            MethodInspection.Builder builder = fqnIsKnown(expressionContext.typeContext(), tempBuilder, false);
            assert builder != null;
            inspectParameters(md.getParameters(), builder.getParameterBuilders(), expressionContext);
            builder.makeParametersImmutable();

            builder.addCompanionMethods(companionMethods);
            checkCompanionMethods(companionMethods, methodName);

            addAnnotations(builder, md.getAnnotations(), newContext);
            if (fullInspection) {
                addModifiers(builder, md.getModifiers());
                if (isInterface) builder.addModifier(MethodModifier.PUBLIC);
                addExceptionTypes(builder, md.getThrownExceptions(), newContext.typeContext());
                ParameterizedType pt = ParameterizedTypeFactory.from(newContext.typeContext(), md.getType());
                builder.setReturnType(pt);

                typeMapBuilder.registerMethodInspection(builder);

                if (md.getBody().isPresent()) {
                    builder.setBlock(md.getBody().get());
                }
            }
        } catch (RuntimeException e) {
            LOGGER.error("Caught exception while inspecting method {} in {}", methodName, typeInfo.fullyQualifiedName());
            throw e;
        }
    }

    private ExpressionContext addTypeParameters(CallableDeclaration<?> md,
                                                ExpressionContext expressionContext,
                                                MethodInspection.Builder tempBuilder) {
        int tpIndex = 0;
        ExpressionContext newContext = md.getTypeParameters().isEmpty() ? expressionContext :
                expressionContext.newTypeContext("Method type parameters");
        for (com.github.javaparser.ast.type.TypeParameter typeParameter : md.getTypeParameters()) {
            TypeParameterImpl tp = new TypeParameterImpl(typeParameter.getNameAsString(), tpIndex++);
            tempBuilder.addTypeParameter(tp);
            newContext.typeContext().addToContext(tp);
            tp.inspect(newContext.typeContext(), typeParameter);
        }
        return newContext;
    }

    private static void addAnnotations(MethodInspection.Builder builder,
                                       NodeList<AnnotationExpr> annotations,
                                       ExpressionContext expressionContext) {
        for (AnnotationExpr ae : annotations) {
            builder.addAnnotation(AnnotationInspector.inspect(expressionContext, ae));
        }
    }

    private static void addExceptionTypes(MethodInspection.Builder builder,
                                          NodeList<ReferenceType> thrownExceptions,
                                          TypeContext typeContext) {
        for (ReferenceType referenceType : thrownExceptions) {
            ParameterizedType pt = ParameterizedTypeFactory.from(typeContext, referenceType);
            builder.addExceptionType(pt);
        }
    }

    private static void addModifiers(MethodInspection.Builder builder, NodeList<Modifier> modifiers) {
        for (Modifier modifier : modifiers) {
            builder.addModifier(MethodModifier.from(modifier));
        }
    }

    private static void addParameters(MethodInspection.Builder builder,
                                      NodeList<Parameter> parameters,
                                      ExpressionContext expressionContext,
                                      DollarResolver dollarResolver) {
        int i = 0;
        for (Parameter parameter : parameters) {
            ParameterizedType pt = ParameterizedTypeFactory.from(expressionContext.typeContext(), parameter.getType(),
                    parameter.isVarArgs(), dollarResolver);
            ParameterInspection.Builder pib = new ParameterInspectionImpl.Builder(Identifier.from(parameter),
                    pt, parameter.getNameAsString(), i++);
            pib.setVarArgs(parameter.isVarArgs());
            // we do not copy annotations yet, that happens after readFQN
            builder.addParameter(pib);
        }
    }

    private static void inspectParameters(NodeList<Parameter> parameters,
                                          List<ParameterInspection.Builder> parameterBuilders,
                                          ExpressionContext expressionContext) {
        int i = 0;
        for (Parameter parameter : parameters) {
            ParameterInspection.Builder builder = parameterBuilders.get(i++);
            builder.copyAnnotations(parameter, expressionContext);
        }
    }
}
