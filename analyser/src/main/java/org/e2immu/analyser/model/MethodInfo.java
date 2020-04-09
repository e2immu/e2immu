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

package org.e2immu.analyser.model;

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.TypeParameter;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.FirstThen;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.E2Immutable;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECT;
import static org.e2immu.analyser.util.Logger.log;

@Container
@E2Immutable(after = "TypeAnalyser.analyse()") // and not MethodAnalyser.analyse(), given the back reference
public class MethodInfo implements WithInspectionAndAnalysis {

    public final TypeInfo typeInfo; // back reference, only @ContextClass after...
    public final String name;
    public final List<ParameterInfo> parametersAsObserved;
    public final ParameterizedType returnTypeObserved; // @ContextClass
    public final boolean isConstructor;
    public final boolean isStatic;
    public final boolean isDefaultImplementation;

    //@Immutable(after="this.inspect(),this.inspect()")
    public final SetOnce<MethodInspection> methodInspection = new SetOnce<>();
    //@Immutable(after="MethodAnalyser.analyse()")
    public final MethodAnalysis methodAnalysis = new MethodAnalysis();

    // for constructors
    public MethodInfo(TypeInfo typeInfo, List<ParameterInfo> parametersAsObserved) {
        this(typeInfo, typeInfo.simpleName, parametersAsObserved, null, true, false, false);
    }

    public MethodInfo(TypeInfo typeInfo, String name, List<ParameterInfo> parametersAsObserved,
                      ParameterizedType returnTypeObserved, boolean isStatic) {
        this(typeInfo, name, parametersAsObserved, returnTypeObserved, false, isStatic, false);
    }

    public MethodInfo(TypeInfo typeInfo, String name, List<ParameterInfo> parametersAsObserved,
                      ParameterizedType returnTypeObserved, boolean isStatic, boolean isDefaultImplementation) {
        this(typeInfo, name, parametersAsObserved, returnTypeObserved, false, isStatic, isDefaultImplementation);
    }

    public MethodInfo(TypeInfo typeInfo, String name, boolean isStatic) {
        this(typeInfo, name, List.of(), null, false, isStatic, false);
    }

    /**
     * it is possible to observe a method without being able to see its return type. That does not make
     * the method a constructor... we cannot use the returnTypeObserved == null as isConstructor
     */
    private MethodInfo(TypeInfo typeInfo, String name, List<ParameterInfo> parametersAsObserved,
                       ParameterizedType returnTypeObserved, boolean isConstructor, boolean isStatic, boolean isDefaultImplementation) {
        Objects.requireNonNull(typeInfo, "Trying to create a method " + name + " but null type");
        Objects.requireNonNull(name);
        Objects.requireNonNull(parametersAsObserved);

        if (isStatic && isConstructor) throw new IllegalArgumentException();
        this.isStatic = isStatic;
        this.typeInfo = typeInfo;
        this.name = name;
        this.parametersAsObserved = parametersAsObserved;
        this.returnTypeObserved = returnTypeObserved;
        this.isConstructor = isConstructor;
        this.isDefaultImplementation = isDefaultImplementation;
        if (isConstructor && returnTypeObserved != null) throw new IllegalArgumentException();
    }

    public boolean hasBeenInspected() {
        return methodInspection.isSet();
    }

    @Override
    public boolean hasBeenDefined() {
        if (!hasBeenInspected()) return false;
        FirstThen<BlockStmt, Block> body = methodInspection.get().methodBody;
        return (body.isSet() && !body.get().statements.isEmpty() ||
                !body.isSet() && !body.getFirst().getStatements().isEmpty());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodInfo that = (MethodInfo) o;
        if (hasBeenInspected()) {
            return methodInspection.get().fullyQualifiedName.equals(that.methodInspection.get().fullyQualifiedName);
        }
        return typeInfo.equals(that.typeInfo) &&
                name.equals(that.name) &&
                parametersAsObserved.equals(that.parametersAsObserved);
    }

    @Override
    public int hashCode() {
        if (hasBeenInspected()) {
            return Objects.hash(methodInspection.get().fullyQualifiedName);
        }
        return Objects.hash(typeInfo, name, parametersAsObserved);
    }

    @Override
    public Inspection getInspection() {
        return methodInspection.get();
    }

    @Override
    public Analysis getAnalysis() {
        return methodAnalysis;
    }

    public void inspect(AnnotationMemberDeclaration amd, ExpressionContext expressionContext) {
        log(INSPECT, "Inspecting annotation member {}", fullyQualifiedName());
        MethodInspection.MethodInspectionBuilder builder = new MethodInspection.MethodInspectionBuilder();
        addAnnotations(builder, amd.getAnnotations(), expressionContext);
        addModifiers(builder, amd.getModifiers());
        Expression expression = expressionContext.parseExpression(amd.getDefaultValue());
        Block body = new Block.BlockBuilder().addStatement(new ReturnStatement(expression)).build();
        builder.setBlock(body);
        ParameterizedType returnType = ParameterizedType.from(expressionContext.typeContext, amd.getType());
        builder.setReturnType(returnType);
        methodInspection.set(builder.build(this));
    }


    public void inspect(ConstructorDeclaration cd, ExpressionContext expressionContext) {
        log(INSPECT, "Inspecting constructor {}", fullyQualifiedName());
        MethodInspection.MethodInspectionBuilder builder = new MethodInspection.MethodInspectionBuilder();
        addAnnotations(builder, cd.getAnnotations(), expressionContext);
        addModifiers(builder, cd.getModifiers());
        addParameters(builder, cd.getParameters(), expressionContext);
        addExceptionTypes(builder, cd.getThrownExceptions(), expressionContext.typeContext);
        builder.setBlock(cd.getBody());
        methodInspection.set(builder.build(this));
    }

    public void inspect(boolean isInterface, MethodDeclaration md, ExpressionContext expressionContext) {
        log(INSPECT, "Inspecting method {}", fullyQualifiedName());
        MethodInspection.MethodInspectionBuilder builder = new MethodInspection.MethodInspectionBuilder();
        int tpIndex = 0;
        TypeContext typeContextWithParameters = md.getTypeParameters().isNonEmpty() ?
                new TypeContext(expressionContext.typeContext) : expressionContext.typeContext;
        for (TypeParameter typeParameter : md.getTypeParameters()) {
            org.e2immu.analyser.model.TypeParameter tp = new org.e2immu.analyser.model.TypeParameter(this, typeParameter.getNameAsString(), tpIndex++);
            builder.addTypeParameter(tp);
            expressionContext.typeContext.addToContext(tp);
            typeContextWithParameters.addToContext(tp);
        }
        addAnnotations(builder, md.getAnnotations(), expressionContext);
        addModifiers(builder, md.getModifiers());
        if (isInterface) builder.addModifier(MethodModifier.PUBLIC);
        addParameters(builder, md.getParameters(), expressionContext);
        addExceptionTypes(builder, md.getThrownExceptions(), expressionContext.typeContext);
        ParameterizedType pt = ParameterizedType.from(typeContextWithParameters, md.getType());
        builder.setReturnType(pt);
        if (md.getBody().isPresent()) {
            builder.setBlock(md.getBody().get());
        }
        methodInspection.set(builder.build(this));
    }

    private void addAnnotations(MethodInspection.MethodInspectionBuilder builder, NodeList<AnnotationExpr> annotations, ExpressionContext expressionContext) {
        for (AnnotationExpr ae : annotations) {
            builder.addAnnotation(AnnotationExpression.from(ae, expressionContext));
        }
    }

    private void addExceptionTypes(MethodInspection.MethodInspectionBuilder builder,
                                   NodeList<ReferenceType> thrownExceptions,
                                   TypeContext typeContext) {
        for (ReferenceType referenceType : thrownExceptions) {
            ParameterizedType pt = ParameterizedType.from(typeContext, referenceType);
            builder.addExceptionType(pt);
        }
    }

    private void addModifiers(MethodInspection.MethodInspectionBuilder builder, NodeList<Modifier> modifiers) {
        for (Modifier modifier : modifiers) {
            if (!"static".equals(modifier.getKeyword().asString()))
                builder.addModifier(MethodModifier.from(modifier));
        }
    }

    private void addParameters(MethodInspection.MethodInspectionBuilder builder, NodeList<Parameter> parameters,
                               ExpressionContext expressionContext) {
        int i = 0;
        for (Parameter parameter : parameters) {
            ParameterizedType pt = ParameterizedType.from(expressionContext.typeContext, parameter.getType(), parameter.isVarArgs());
            ParameterInfo parameterInfo = new ParameterInfo(pt, parameter.getNameAsString(), i++);
            parameterInfo.inspect(this, parameter, expressionContext, parameter.isVarArgs());
            builder.addParameter(parameterInfo);
        }
    }

    public Set<String> imports() {
        Set<String> imports = new HashSet<>();
        if (!isConstructor) {
            imports.addAll(hasBeenInspected() ? methodInspection.get().returnType.imports() :
                    returnTypeObserved.imports());
        }
        for (ParameterInfo parameterInfo : (hasBeenInspected() ? methodInspection.get().parameters : parametersAsObserved)) {
            imports.addAll(parameterInfo.imports());
        }
        if (hasBeenInspected()) {
            for (AnnotationExpression annotationExpression : methodInspection.get().annotations) {
                imports.addAll(annotationExpression.imports());
            }
            for (ParameterizedType parameterizedType : methodInspection.get().exceptionTypes) {
                imports.addAll(parameterizedType.imports());
            }
            if (methodInspection.get().methodBody.isSet()) {
                imports.addAll(methodInspection.get().methodBody.get().imports());
            }
        }
        return imports;
    }

    public String stream(int indent) {
        StringBuilder sb = new StringBuilder();
        ParameterizedType returnType;
        if (hasBeenInspected()) {
            returnType = isConstructor ? null : methodInspection.get().returnType;
        } else {
            returnType = returnTypeObserved;
        }

        List<MethodModifier> methodModifiers;
        if (hasBeenInspected()) {
            methodModifiers = methodInspection.get().modifiers;
        } else {
            methodModifiers = List.of(MethodModifier.PUBLIC);
        }
        if (hasBeenInspected()) {
            for (AnnotationExpression annotation : methodInspection.get().annotations) {
                StringUtil.indent(sb, indent);
                sb.append(annotation.stream());
                sb.append("\n");
            }
            methodAnalysis.annotations.visit((annotation, present) -> {
                if (present) {
                    StringUtil.indent(sb, indent);
                    sb.append(annotation.stream());
                    sb.append("\n");
                }
            });
        }
        StringUtil.indent(sb, indent);
        sb.append(methodModifiers.stream().map(m -> m.toJava() + " ").collect(Collectors.joining()));
        if (isStatic) {
            sb.append("static ");
        }
        if (hasBeenInspected()) {
            MethodInspection methodInspection = this.methodInspection.get();
            if (!methodInspection.typeParameters.isEmpty()) {
                sb.append("<");
                sb.append(methodInspection.typeParameters.stream().map(tp -> tp.name).collect(Collectors.joining(", ")));
                sb.append("> ");
            }
        }
        if (!isConstructor) {
            sb.append(returnType.stream());
            sb.append(" ");
        }
        sb.append(name);
        sb.append("(");

        List<ParameterInfo> parameters;
        if (hasBeenInspected()) {
            parameters = methodInspection.get().parameters;
        } else {
            parameters = parametersAsObserved;
        }
        sb.append(parameters.stream().map(ParameterInfo::stream).collect(Collectors.joining(", ")));
        sb.append(")");
        if (hasBeenInspected() && !methodInspection.get().exceptionTypes.isEmpty()) {
            sb.append(" throws ");
            sb.append(methodInspection.get().exceptionTypes.stream()
                    .map(ParameterizedType::stream).collect(Collectors.joining(", ")));
        }
        if (hasBeenInspected() && methodInspection.get().methodBody.isSet()) {
            sb.append(methodInspection.get().methodBody.get().statementString(indent));
        } else {
            sb.append(" { }");
        }
        return sb.toString();
    }

    public String fullyQualifiedName() {
        if (methodInspection.isSet()) {
            return methodInspection.get().fullyQualifiedName;
        }
        return typeInfo.fullyQualifiedName + "." + name + "()";
    }

    public String distinguishingName() {
        if (methodInspection.isSet()) {
            return methodInspection.get().distinguishingName;
        }
        return typeInfo.fullyQualifiedName + "." + name + "()";
    }

    public ParameterizedType returnType() {
        return Objects.requireNonNull(hasBeenInspected() ? methodInspection.get().returnType :
                returnTypeObserved, "Null return type for " + fullyQualifiedName());
    }

    @Override
    public Optional<AnnotationExpression> hasTestAnnotation(Class<?> annotation) {
        if (!hasBeenDefined()) return Optional.empty();
        String annotationFQN = annotation.getName();
        Optional<AnnotationExpression> fromMethod = (getInspection().annotations.stream()
                .filter(ae -> ae.typeInfo.fullyQualifiedName.equals(annotationFQN))).findFirst();
        if (fromMethod.isPresent()) return fromMethod;
        Optional<AnnotationExpression> fromType = typeInfo.hasTestAnnotation(annotation);
        if (fromType.isPresent()) return fromType;
        if (methodInspection.isSet()) {
            for (MethodInfo interfaceMethod : methodInspection.get().implementationOf) {
                Optional<AnnotationExpression> fromInterface = (interfaceMethod.hasTestAnnotation(annotation));
                if (fromInterface.isPresent()) return fromInterface;
            }
        }
        return Optional.empty();
    }

    public Boolean annotatedWithCheckOverloads(AnnotationExpression annotation, TypeContext typeContext) {
        Boolean result = annotatedWith(annotation);
        if (result != Boolean.FALSE) return result;
        for (MethodInfo methodInfo : typeInfo.overloads(this, typeContext)) {
            Boolean resultFromOverload = methodInfo.annotatedWith(annotation);
            if (resultFromOverload != Boolean.FALSE) return resultFromOverload;
        }
        return false;
    }

    public Boolean isFluent(TypeContext typeContext) {
        return annotatedWithCheckOverloads(typeContext.fluent.get(), typeContext);
    }

    public Boolean isIdentity(TypeContext typeContext) {
        return annotatedWithCheckOverloads(typeContext.identity.get(), typeContext);
    }

    public Boolean isIndependent(TypeContext typeContext) {
        return annotatedWithCheckOverloads(typeContext.independent.get(), typeContext);
    }

    // this one is not "inheritable" but a shorthand to allow us not to have to write...
    public Boolean isNotNull(TypeContext typeContext) {
        if (typeInfo.annotatedWith(typeContext.notNull.get()) == Boolean.TRUE) return true;
        return annotatedWith(typeContext.notNull.get());
    }

    // this one is both inheritable and shortcut-able
    public Boolean isNotModified(TypeContext typeContext) {
        if (typeInfo.annotatedWith(typeContext.notNull.get()) == Boolean.TRUE) return true;
        return annotatedWithCheckOverloads(typeContext.notModified.get(), typeContext);
    }

    public SideEffect sideEffect(TypeContext typeContext) {
        Boolean sseo = isStaticSideEffectsOnly(typeContext);
        if (Boolean.TRUE == sseo) return SideEffect.STATIC_ONLY;

        Boolean context = isNotModified(typeContext);
        if (Boolean.TRUE == context) return SideEffect.NONE_CONTEXT;

        Boolean pure = isNotModified(typeContext);
        if (Boolean.TRUE == pure) return SideEffect.NONE_PURE;

        if (sseo == null && pure == null && context == null) return SideEffect.DELAYED;
        return SideEffect.SIDE_EFFECT;
    }

    // given R accept(T t), and types={string}, returnType=string, deduce that R=string, T=string, and we have Function<String, String>
    public List<ParameterizedType> typeParametersComputed(List<ParameterizedType> types, ParameterizedType inferredReturnType) {
        if (typeInfo.typeInspection.get().typeParameters.isEmpty()) return List.of();
        return typeInfo.typeInspection.get().typeParameters.stream().map(typeParameter -> {
            int cnt = 0;
            for (ParameterInfo parameterInfo : methodInspection.get().parameters) {
                if (parameterInfo.parameterizedType.typeParameter == typeParameter) {
                    return types.get(cnt); // this is one we know!
                }
                cnt++;
            }
            if (methodInspection.get().returnType.typeParameter == typeParameter) return inferredReturnType;
            return new ParameterizedType(typeParameter, 0, ParameterizedType.WildCard.NONE);
        }).collect(Collectors.toList());
    }

    @Override
    public String name() {
        return name;
    }

    public Boolean isStaticSideEffectsOnly(TypeContext typeContext) {
        if (!isStatic) return false;
        Boolean notModified = annotatedWith(typeContext.notModified.get());
        if (notModified == null) return null;
        if (!notModified) return false;
        if (methodInspection.get().returnType.isVoid()) {
            return true;
        }
        Boolean isFluent = annotatedWith(typeContext.fluent.get());
        return isFluent == Boolean.TRUE;
    }

    public Boolean isAllParametersNotModified(TypeContext typeContext) {
        for (ParameterInfo parameterInfo : methodInspection.get().parameters) {
            Boolean isNotModified = parameterInfo.isNotModified(typeContext);
            if (isNotModified == null) return null; // no idea
            if (!isNotModified) return false;
        }
        return true;
    }

    public Boolean isAllFieldsNotModified(TypeContext typeContext) {
        if (methodAnalysis.fieldModifications.isEmpty()) return true;
        return methodAnalysis.fieldModifications.stream().noneMatch(e -> e.getValue() == Boolean.TRUE);
    }

    public boolean sameMethod(MethodInfo target) {
        return name.equals(target.name) &&
                sameParameters(methodInspection.get().parameters, target.methodInspection.get().parameters);
    }

    private static boolean sameParameters(List<ParameterInfo> list1, List<ParameterInfo> list2) {
        if (list1.size() != list2.size()) return false;
        int i = 0;
        for (ParameterInfo parameterInfo : list1) {
            ParameterInfo p2 = list2.get(i);
            if (differentType(parameterInfo.parameterizedType, p2.parameterizedType)) return false;
            i++;
        }
        return true;
    }

    private static boolean differentType(ParameterizedType pt1, ParameterizedType pt2) {
        Objects.requireNonNull(pt1);
        Objects.requireNonNull(pt2);
        if (pt1 == ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR && pt2 == pt1) return false;

        if (pt1.typeInfo != null) {
            if (pt2.typeInfo != pt1.typeInfo) return true;
            if (pt1.parameters.size() != pt2.parameters.size()) return true;
            int i = 0;
            for (ParameterizedType param1 : pt1.parameters) {
                ParameterizedType param2 = pt2.parameters.get(i);
                if (differentType(param1, param2)) return true;
                i++;
            }
            return false;
        }
        if (pt2.typeInfo != null) return true;
        if (pt1.typeParameter == null && pt2.typeParameter == null) return false;
        return pt1.typeParameter == null || pt2.typeParameter == null ||
                pt1.typeParameter.index != pt2.typeParameter.index ||
                pt1.typeParameter.owner.isLeft() != pt2.typeParameter.owner.isLeft();
    }

    @Override
    public String toString() {
        return fullyQualifiedName();
    }

    public boolean isVarargs() {
        MethodInspection mi = methodInspection.get();
        if (mi.parameters.isEmpty()) return false;
        return mi.parameters.get(mi.parameters.size() - 1).parameterInspection.get().varArgs;
    }
}
