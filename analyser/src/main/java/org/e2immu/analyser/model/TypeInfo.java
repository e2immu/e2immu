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
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.*;
import org.e2immu.analyser.parser.expr.ParseLambdaExpr;
import org.e2immu.analyser.util.*;
import org.e2immu.annotation.AnnotationType;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.parser.Primitives.PRIMITIVES;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
import static org.e2immu.analyser.util.Logger.log;

public class TypeInfo implements NamedType, WithInspectionAndAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeInfo.class);

    @NotNull
    public final String simpleName;
    @NotNull
    public final String fullyQualifiedName;

    //@Immutable(after="this.inspect()")
    public final SetTwiceSupply<TypeInspection> typeInspection = new SetTwiceSupply<>();
    public final SetOnce<TypeAnalysis> typeAnalysis = new SetOnce<>();

    // creates an anonymous version of the parent type parameterizedType
    public TypeInfo(TypeInfo enclosingType, int number) {
        simpleName = enclosingType.simpleName + "$" + number;
        fullyQualifiedName = enclosingType.fullyQualifiedName + "$" + number;
    }

    @Override
    public String name() {
        return simpleName;
    }

    public TypeInfo(@NotNull String packageName, @NotNull String simpleName) {
        if (Objects.requireNonNull(packageName).isEmpty())
            throw new UnsupportedOperationException("Expect a non-empty package name for " + simpleName);
        this.fullyQualifiedName = packageName + "." + simpleName;
        this.simpleName = Objects.requireNonNull(simpleName);
    }

    public TypeInfo(@NotNull String fullyQualifiedName) {
        this.fullyQualifiedName = fullyQualifiedName;
        int dot = fullyQualifiedName.lastIndexOf('.');
        if (dot >= 0) {
            simpleName = fullyQualifiedName.substring(dot + 1);
        } else {
            simpleName = fullyQualifiedName;
        }
    }

    @Override
    public Inspection getInspection() {
        return typeInspection.getPotentiallyRun();
    }

    @Override
    @NotNull
    public Analysis getAnalysis() {
        return typeAnalysis.get();
    }

    public boolean hasBeenInspected() {
        return typeInspection.isSetPotentiallyRun();
    }

    @Override
    public boolean hasBeenDefined() {
        return hasBeenInspected() && typeInspection.getPotentiallyRun().hasBeenDefined;
    }

    public void inspectAnonymousType(ParameterizedType classImplemented,
                                     ExpressionContext expressionContext,
                                     NodeList<BodyDeclaration<?>> members) {
        TypeInspection.TypeInspectionBuilder builder = new TypeInspection.TypeInspectionBuilder();
        builder.setEnclosingType(expressionContext.enclosingType);
        if (classImplemented.typeInfo.typeInspection.getPotentiallyRun().typeNature == TypeNature.INTERFACE) {
            builder.addInterfaceImplemented(classImplemented);
        } else {
            builder.setParentClass(classImplemented);
        }
        continueInspection(true, expressionContext, builder, members, false, false);
    }

    /**
     * @param hasBeenDefined           when true, parsing .java; otherwise, parsing .annotated_api
     * @param enclosingTypeIsInterface when true, the enclosing type is an interface, and we need to add PUBLIC
     * @param enclosingType            when not null, denotes the parent type; otherwise, this is a primary type
     * @param typeDeclaration          the JavaParser object to inspect
     * @param expressionContext        the context to inspect in
     */
    public void inspect(boolean hasBeenDefined,
                        boolean enclosingTypeIsInterface,
                        TypeInfo enclosingType,
                        TypeDeclaration<?> typeDeclaration,
                        ExpressionContext expressionContext) {
        LOGGER.info("Inspecting type {}", fullyQualifiedName);
        TypeInspection.TypeInspectionBuilder builder = new TypeInspection.TypeInspectionBuilder();
        if (enclosingType != null) {
            builder.setEnclosingType(enclosingType);
        } else {
            builder.setPackageName(computePackageName());
        }
        expressionContext.typeContext.addToContext(this);

        TypeNature typeNature = typeNature(typeDeclaration);
        builder.setTypeNature(typeNature);

        if (enclosingTypeIsInterface) {
            builder.addTypeModifier(TypeModifier.PUBLIC);
            if (typeNature == TypeNature.INTERFACE) {
                builder.addTypeModifier(TypeModifier.STATIC);
            }
        }

        if (typeDeclaration instanceof EnumDeclaration) {
            doEnumDeclaration(expressionContext, (EnumDeclaration) typeDeclaration, builder);
        }
        if (typeDeclaration instanceof AnnotationDeclaration) {
            doAnnotationDeclaration(expressionContext, (AnnotationDeclaration) typeDeclaration, builder);
        }
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration) {
            doClassOrInterfaceDeclaration(hasBeenDefined, expressionContext, typeNature, (ClassOrInterfaceDeclaration) typeDeclaration, builder);
        }
        boolean haveFunctionalInterface = false;
        for (AnnotationExpr annotationExpr : typeDeclaration.getAnnotations()) {
            AnnotationExpression ae = AnnotationExpression.from(annotationExpr, expressionContext);
            haveFunctionalInterface |= "java.lang.FunctionalInterface".equals(ae.typeInfo.fullyQualifiedName);
            builder.addAnnotation(ae);
            if (!hasBeenDefined) {
                ae.resolve(expressionContext);
            } // we'll do it later during the resolution phase
        }
        for (Modifier modifier : typeDeclaration.getModifiers()) {
            builder.addTypeModifier(TypeModifier.from(modifier));
        }
        continueInspection(hasBeenDefined, expressionContext, builder, typeDeclaration.getMembers(), typeNature == TypeNature.INTERFACE, haveFunctionalInterface);
    }

    private void doAnnotationDeclaration(ExpressionContext expressionContext, AnnotationDeclaration annotationDeclaration,
                                         TypeInspection.TypeInspectionBuilder builder) {
        builder.setTypeNature(TypeNature.ANNOTATION);
        ExpressionContext subContext = expressionContext.newVariableContext("annotation body of " + fullyQualifiedName);

        for (BodyDeclaration<?> bd : annotationDeclaration.getMembers()) {
            if (bd.isAnnotationMemberDeclaration()) {
                AnnotationMemberDeclaration amd = bd.asAnnotationMemberDeclaration();
                log(INSPECT, "Have member {} in {}", amd.getNameAsString(), fullyQualifiedName);
                String methodName = amd.getName().getIdentifier();
                MethodInfo methodInfo = new MethodInfo(this, methodName, List.of(),
                        PRIMITIVES.voidParameterizedType, true, true);
                methodInfo.inspect(amd, subContext);

                builder.addMethod(methodInfo);
            }
        }
    }

    private void doEnumDeclaration(ExpressionContext expressionContext,
                                   EnumDeclaration enumDeclaration,
                                   TypeInspection.TypeInspectionBuilder builder) {
        builder.setTypeNature(TypeNature.ENUM);
        enumDeclaration.getEntries().forEach(enumConstantDeclaration -> {
            FieldInfo fieldInfo = new FieldInfo(this, enumConstantDeclaration.getNameAsString(), this);
            FieldInspection.FieldInspectionBuilder fieldBuilder = new FieldInspection.FieldInspectionBuilder();
            fieldBuilder.addModifier(FieldModifier.FINAL);
            fieldBuilder.addModifier(FieldModifier.PUBLIC);
            fieldBuilder.addModifier(FieldModifier.STATIC);
            fieldInfo.fieldInspection.set(fieldBuilder.build());
            builder.addField(fieldInfo);
            // TODO we have arguments, class body
        });
        MethodInfo nameMethodInfo = new MethodInfo(this, "name", List.of(),
                PRIMITIVES.stringParameterizedType, false);
        nameMethodInfo.methodInspection.set(new MethodInspection.MethodInspectionBuilder()
                .addAnnotation(expressionContext.e2ImmuAnnotationExpressions.notModified.get())
                .setReturnType(PRIMITIVES.stringParameterizedType)
                .build(nameMethodInfo));

        MethodInfo valueOfMethodInfo = new MethodInfo(this, "valueOf", List.of(),
                PRIMITIVES.stringParameterizedType, true);
        ParameterInfo valueOfP0 = new ParameterInfo(valueOfMethodInfo, PRIMITIVES.stringParameterizedType, "name", 0);
        valueOfP0.parameterInspection.set(new ParameterInspection.ParameterInspectionBuilder()
                .addAnnotation(expressionContext.e2ImmuAnnotationExpressions.notNull.get())
                .build());
        valueOfMethodInfo.methodInspection.set(new MethodInspection.MethodInspectionBuilder()
                .addAnnotation(expressionContext.e2ImmuAnnotationExpressions.notModified.get())
                .setReturnType(asParameterizedType())
                .addParameter(valueOfP0)
                .build(valueOfMethodInfo));

        builder.addMethod(nameMethodInfo).addMethod(valueOfMethodInfo);
    }

    private void doClassOrInterfaceDeclaration(
            boolean hasBeenDefined,
            ExpressionContext expressionContext,
            TypeNature typeNature,
            ClassOrInterfaceDeclaration cid,
            TypeInspection.TypeInspectionBuilder builder) {
        int tpIndex = 0;
        for (com.github.javaparser.ast.type.TypeParameter typeParameter : cid.getTypeParameters()) {
            TypeParameter tp = new TypeParameter(this, typeParameter.getNameAsString(), tpIndex++);
            expressionContext.typeContext.addToContext(tp);
            tp.inspect(expressionContext.typeContext, typeParameter);
            builder.addTypeParameter(tp);
        }
        if (typeNature == TypeNature.CLASS) {
            if (!cid.getExtendedTypes().isEmpty()) {
                ParameterizedType parameterizedType = ParameterizedType.from(expressionContext.typeContext, cid.getExtendedTypes(0));
                // why this check? hasBeenDefined == true signifies Java parsing; == false is annotated APIs.
                // the annotated APIs are backed by .class files, which can be inspected with byte code; there, we only have
                // fully qualified names. In Java, we must add type names of parent's subtypes etc.
                if (hasBeenDefined) ensureLoaded(expressionContext, parameterizedType);
                builder.setParentClass(parameterizedType);
            }
            for (ClassOrInterfaceType extended : cid.getImplementedTypes()) {
                ParameterizedType parameterizedType = ParameterizedType.from(expressionContext.typeContext, extended);
                if (hasBeenDefined) ensureLoaded(expressionContext, parameterizedType);
                builder.addInterfaceImplemented(parameterizedType);
            }
        } else {
            if (typeNature != TypeNature.INTERFACE) throw new UnsupportedOperationException();
            for (ClassOrInterfaceType extended : cid.getExtendedTypes()) {
                ParameterizedType parameterizedType = ParameterizedType.from(expressionContext.typeContext, extended);
                if (hasBeenDefined) ensureLoaded(expressionContext, parameterizedType);
                builder.addInterfaceImplemented(parameterizedType);
            }
        }
    }

    /**
     * calling "get" on the typeInspection of the parameterizedType will trigger recursive parsing.
     * But we should not do that when we're inside the same compilation unit: the primary type and all its subtypes.
     * <p>
     * We should be wary of loops here; we may have to extend this whole system, and somehow keep track of
     * all types currently under inspection, as we do with the bytecode inspector.
     */
    private void ensureLoaded(ExpressionContext expressionContext, ParameterizedType parameterizedType) {
        boolean insideCompilationUnit = parameterizedType.typeInfo.fullyQualifiedName.startsWith(expressionContext.primaryType.fullyQualifiedName);
        if (!insideCompilationUnit) {
            parameterizedType.typeInfo.typeInspection.getPotentiallyRun(parameterizedType.typeInfo.fullyQualifiedName);
            // now that we're sure it has been inspected, we add all its top-level subtypes to the type context
            TypeInspection typeInspection = parameterizedType.typeInfo.typeInspection.getPotentiallyRun();
            for (TypeInfo subType : typeInspection.subTypes) {
                expressionContext.typeContext.addToContext(subType);
            }
            if (typeInspection.parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT) {
                ensureLoaded(expressionContext, typeInspection.parentClass);
            }
            typeInspection.interfacesImplemented.forEach(i -> ensureLoaded(expressionContext, i));
        }
    }

    public void inspectLocalClassDeclaration(ExpressionContext expressionContext, ClassOrInterfaceDeclaration cid) {
        TypeInspection.TypeInspectionBuilder builder = new TypeInspection.TypeInspectionBuilder();
        builder.setEnclosingType(expressionContext.enclosingType);
        doClassOrInterfaceDeclaration(true, expressionContext, TypeNature.CLASS, cid, builder);
        continueInspection(true, expressionContext, builder, cid.getMembers(), false, false);
    }


    public void recursivelyAddToTypeStore(TypeStore typeStore, TypeDeclaration<?> typeDeclaration) {
        typeDeclaration.getMembers().forEach(bodyDeclaration -> {
            bodyDeclaration.ifClassOrInterfaceDeclaration(cid -> {
                TypeInfo subType = new TypeInfo(fullyQualifiedName, cid.getName().asString());
                typeStore.add(subType);
                log(INSPECT, "Added to type store: " + subType.fullyQualifiedName);
                subType.recursivelyAddToTypeStore(typeStore, cid);
            });
            bodyDeclaration.ifEnumDeclaration(ed -> {
                TypeInfo subType = new TypeInfo(fullyQualifiedName, ed.getName().asString());
                typeStore.add(subType);
                log(INSPECT, "Added enum to type store: " + subType.fullyQualifiedName);
                subType.recursivelyAddToTypeStore(typeStore, ed);
            });
        });
    }

    private void continueInspection(
            boolean hasBeenDefined,
            ExpressionContext expressionContext,
            TypeInspection.TypeInspectionBuilder builder,
            NodeList<BodyDeclaration<?>> members,
            boolean isInterface,
            boolean haveFunctionalInterface) {
        // first, do sub-types
        ExpressionContext subContext = expressionContext.newVariableContext("body of " + fullyQualifiedName);

        // 2 step approach: first, add these types to the expression context, without inspection
        for (BodyDeclaration<?> bodyDeclaration : members) {
            bodyDeclaration.ifClassOrInterfaceDeclaration(cid -> {
                TypeInfo subType = expressionContext.typeContext.typeStore.get(fullyQualifiedName + "." + cid.getNameAsString());
                if (subType == null)
                    throw new UnsupportedOperationException("I should already know type " + cid.getNameAsString() + " inside " + fullyQualifiedName);
                expressionContext.typeContext.addToContext(subType);
            });
            bodyDeclaration.ifEnumDeclaration(ed -> {
                TypeInfo subType = expressionContext.typeContext.typeStore.get(fullyQualifiedName + "." + ed.getNameAsString());
                if (subType == null)
                    throw new UnsupportedOperationException("I should already know enum type " + ed + " inside " + fullyQualifiedName);
                expressionContext.typeContext.addToContext(subType);
            });
        }

        // then inspect them...
        for (BodyDeclaration<?> bodyDeclaration : members) {
            bodyDeclaration.ifClassOrInterfaceDeclaration(cid -> {
                TypeInfo subType = expressionContext.typeContext.typeStore.get(fullyQualifiedName + "." + cid.getNameAsString());
                ExpressionContext newExpressionContext = expressionContext.newSubType(subType);
                subType.inspect(hasBeenDefined, isInterface, this, cid.asTypeDeclaration(), newExpressionContext);
                builder.addSubType(subType);
            });
            bodyDeclaration.ifEnumDeclaration(ed -> {
                TypeInfo subType = expressionContext.typeContext.typeStore.get(fullyQualifiedName + "." + ed.getNameAsString());
                ExpressionContext newExpressionContext = expressionContext.newSubType(subType);
                subType.inspect(hasBeenDefined, isInterface, this, ed.asTypeDeclaration(), newExpressionContext);
                builder.addSubType(subType);
            });
        }

        // then, do fields

        for (BodyDeclaration<?> bodyDeclaration : members) {
            bodyDeclaration.ifFieldDeclaration(fd -> {
                List<FieldModifier> modifiers = fd.getModifiers().stream()
                        .map(FieldModifier::from)
                        .collect(Collectors.toList());
                List<AnnotationExpression> annotations = fd.getAnnotations().stream()
                        .map(ae -> AnnotationExpression.from(ae, expressionContext)).collect(Collectors.toList());
                for (VariableDeclarator vd : fd.getVariables()) {
                    ParameterizedType pt = ParameterizedType.from(expressionContext.typeContext, vd.getType());

                    String name = vd.getNameAsString();
                    FieldInfo fieldInfo = new FieldInfo(pt, name, this);
                    FieldInspection.FieldInspectionBuilder fieldInspectionBuilder =
                            new FieldInspection.FieldInspectionBuilder()
                                    .addAnnotations(annotations)
                                    .addModifiers(modifiers);
                    if (isInterface) {
                        fieldInspectionBuilder
                                .addModifier(FieldModifier.STATIC)
                                .addModifier(FieldModifier.FINAL)
                                .addModifier(FieldModifier.PUBLIC);
                    }
                    if (vd.getInitializer().isPresent()) {
                        fieldInspectionBuilder.setInitializer(vd.getInitializer().get());
                    }
                    fieldInfo.fieldInspection.set(fieldInspectionBuilder.build());
                    builder.addField(fieldInfo);
                }
            });
        }

        // finally, do constructors and methods

        log(INSPECT, "Variable context after parsing fields of type {}: {}", fullyQualifiedName, subContext.variableContext);

        AtomicInteger countNonStaticNonDefaultIfInterface = new AtomicInteger();

        for (BodyDeclaration<?> bodyDeclaration : members) {
            bodyDeclaration.ifConstructorDeclaration(cd -> {
                MethodInfo methodInfo = new MethodInfo(this, List.of());
                methodInfo.inspect(cd, subContext);
                builder.addConstructor(methodInfo);
            });
            bodyDeclaration.ifMethodDeclaration(md -> {
                // NOTE: it is possible that the return type is unknown at this moment: it can be one of the type
                // parameters that we'll be parsing soon at inspection. That's why we can live with "void" for now
                String methodName = md.getName().getIdentifier();
                MethodInfo methodInfo = new MethodInfo(this, methodName, List.of(),
                        PRIMITIVES.voidParameterizedType, md.isStatic(), md.isDefault());
                methodInfo.inspect(isInterface, md, subContext);
                if (isInterface && !methodInfo.isStatic && !methodInfo.isDefaultImplementation) {
                    countNonStaticNonDefaultIfInterface.incrementAndGet();
                }
                builder.addMethod(methodInfo);
            });
        }

        if (countNonStaticNonDefaultIfInterface.get() == 1 && !haveFunctionalInterface && hasBeenDefined) {
            boolean haveNonStaticNonDefaultsInSuperType = false;
            for (ParameterizedType superInterface : builder.getInterfacesImplemented()) {
                if (superInterface.typeInfo.haveNonStaticNonDefaultMethods()) {
                    haveNonStaticNonDefaultsInSuperType = true;
                    break;
                }
            }
            if (!haveNonStaticNonDefaultsInSuperType) {
                builder.addAnnotation(PRIMITIVES.functionalInterfaceAnnotationExpression);
            }
        }
        typeInspection.set(builder.build(hasBeenDefined, this));
    }

    private boolean haveNonStaticNonDefaultMethods() {
        if (typeInspection.getPotentiallyRun().methodStream(TypeInspection.Methods.EXCLUDE_FIELD_SAM)
                .anyMatch(m -> !m.isStatic && !m.isDefaultImplementation)) return true;
        for (ParameterizedType superInterface : typeInspection.getPotentiallyRun().interfacesImplemented) {
            if (superInterface.typeInfo.haveNonStaticNonDefaultMethods()) {
                return true;
            }
        }
        return false;
    }

    public Stream<TypeInfo> accessibleBySimpleNameTypeInfoStream() {
        return accessibleBySimpleNameTypeInfoStream(this, new HashSet<>());
    }

    private Stream<TypeInfo> accessibleBySimpleNameTypeInfoStream(TypeInfo startingPoint, Set<TypeInfo> visited) {
        if (visited.contains(this)) return Stream.empty();
        visited.add(this);
        Stream<TypeInfo> mySelf = Stream.of(this);

        TypeInspection typeInspection = this.typeInspection.getPotentiallyRun();
        boolean inSameCompilationUnit = this == startingPoint || primaryType() == startingPoint.primaryType();
        boolean inSamePackage = !inSameCompilationUnit &&
                primaryType().typeInspection.getPotentiallyRun().packageNameOrEnclosingType.getLeft().equals(
                        startingPoint.primaryType().typeInspection.getPotentiallyRun().packageNameOrEnclosingType.getLeft());

        Stream<TypeInfo> localStream = typeInspection.subTypes
                .stream()
                .filter(typeInfo -> inSameCompilationUnit ||
                        typeInfo.typeInspection.getPotentiallyRun().access == TypeModifier.PUBLIC ||
                        inSamePackage && typeInfo.typeInspection.getPotentiallyRun().access == TypeModifier.PACKAGE ||
                        !inSamePackage && typeInfo.typeInspection.getPotentiallyRun().access == TypeModifier.PROTECTED);
        Stream<TypeInfo> parentStream;
        if (typeInspection.parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT) {
            parentStream = typeInspection.parentClass.typeInfo.accessibleBySimpleNameTypeInfoStream(startingPoint, visited);
        } else parentStream = Stream.empty();

        Stream<TypeInfo> joint = Stream.concat(Stream.concat(mySelf, localStream), parentStream);
        for (ParameterizedType interfaceType : typeInspection.interfacesImplemented) {
            Stream<TypeInfo> fromInterface = interfaceType.typeInfo.accessibleBySimpleNameTypeInfoStream(startingPoint, visited);
            joint = Stream.concat(joint, fromInterface);
        }
        return joint;
    }

    public Stream<FieldInfo> accessibleFieldsStream() {
        return accessibleFieldsStream(this);
    }

    private Stream<FieldInfo> accessibleFieldsStream(TypeInfo startingPoint) {
        TypeInspection typeInspection = this.typeInspection.getPotentiallyRun();
        boolean inSameCompilationUnit = this == startingPoint || primaryType() == startingPoint.primaryType();
        boolean inSamePackage = !inSameCompilationUnit &&
                primaryType().typeInspection.getPotentiallyRun().packageNameOrEnclosingType.getLeft().equals(
                        startingPoint.primaryType().typeInspection.getPotentiallyRun().packageNameOrEnclosingType.getLeft());

        // my own field
        Stream<FieldInfo> localStream = typeInspection.fields
                .stream()
                .filter(fieldInfo -> inSameCompilationUnit ||
                        fieldInfo.fieldInspection.get().access == FieldModifier.PUBLIC ||
                        inSamePackage && fieldInfo.fieldInspection.get().access == FieldModifier.PACKAGE ||
                        !inSamePackage && fieldInfo.fieldInspection.get().access == FieldModifier.PROTECTED);

        // my enclosing type's fields
        Stream<FieldInfo> enclosingStream;
        if (typeInspection.packageNameOrEnclosingType.isRight()) {
            enclosingStream = typeInspection.packageNameOrEnclosingType.getRight().accessibleFieldsStream(startingPoint);
        } else {
            enclosingStream = Stream.empty();
        }
        Stream<FieldInfo> joint = Stream.concat(localStream, enclosingStream);

        // my parent's fields
        Stream<FieldInfo> parentStream;
        if (typeInspection.parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT) {
            parentStream = typeInspection.parentClass.typeInfo.accessibleFieldsStream(startingPoint);
        } else parentStream = Stream.empty();
        joint = Stream.concat(joint, parentStream);

        // my interfaces' fields
        for (ParameterizedType interfaceType : typeInspection.interfacesImplemented) {
            Stream<FieldInfo> fromInterface = interfaceType.typeInfo.accessibleFieldsStream(startingPoint);
            joint = Stream.concat(joint, fromInterface);
        }

        return joint;
    }

    private static TypeNature typeNature(TypeDeclaration<?> typeDeclaration) {
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) typeDeclaration;
            if (cid.isInterface()) {
                return TypeNature.INTERFACE;
            }
            return TypeNature.CLASS;
        }
        if (typeDeclaration instanceof AnnotationDeclaration) {
            return TypeNature.ANNOTATION;
        }
        if (typeDeclaration instanceof EnumDeclaration) {
            return TypeNature.ENUM;
        }
        throw new UnsupportedOperationException();
    }

    private String computePackageName() {
        int dot = fullyQualifiedName.lastIndexOf('.');
        if (dot >= 0) {
            return fullyQualifiedName.substring(0, dot);
        }
        return "";
    }

    public String stream() {
        return stream(0);
    }

    public String stream(int indent) {
        return stream(indent, true);
    }

    public String stream(int indent, boolean doTypeDeclaration) {
        boolean isSubType = indent > 0;
        String typeNature;
        Set<AnnotationExpression> annotations = new HashSet<>();
        Set<String> imports = isSubType ? Collections.emptySet() : imports();
        Stream<String> typeModifiers;
        List<FieldInfo> fields;
        List<MethodInfo> constructors;
        List<MethodInfo> methods;
        List<TypeInfo> subTypes;
        String interfacesCsv = "";
        String typeParametersCsv = "";
        String parentClass = "";
        String packageName;

        if (hasBeenInspected()) {
            TypeInspection typeInspection = this.typeInspection.getPotentiallyRun();
            typeNature = typeInspection.typeNature.toJava();
            typeModifiers = typeInspection.modifiers.stream().map(TypeModifier::toJava);
            packageName = typeInspection.packageNameOrEnclosingType.getLeftOrElse("");
            annotations.addAll(typeInspection.annotations);
            fields = typeInspection.fields;
            constructors = typeInspection.constructors;
            methods = typeInspection.methods;
            subTypes = typeInspection.subTypes;
            parentClass = typeInspection.parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT ? typeInspection.parentClass.stream() : "";
            interfacesCsv = typeInspection.interfacesImplemented.stream()
                    .map(ParameterizedType::stream).collect(Collectors.joining(", "));
            typeParametersCsv = typeInspection.typeParameters.stream()
                    .map(TypeParameter::stream).collect(Collectors.joining(", "));
        } else {
            packageName = computePackageName();
            typeNature = "class"; // we really have no idea what it is
            typeModifiers = List.of("abstract").stream();
            fields = List.of();
            constructors = List.of();
            methods = List.of();
            subTypes = List.of();
        }

        Stream<String> fieldsStream = fields.stream().map(field -> field.stream(indent + 4));
        Stream<String> constructorsStream = constructors.stream().map(method -> method.stream(indent + 4));
        Stream<String> methodsStream = methods.stream().map(method -> method.stream(indent + 4));
        Stream<String> subTypesStream = subTypes.stream().map(subType -> subType.stream(indent + 4));

        boolean isMainType = indent == 0;

        StringBuilder sb = new StringBuilder();
        if (isMainType) {
            if (!packageName.isEmpty()) {
                sb.append("package ");
                sb.append(packageName);
                sb.append(";\n\n");
            }
            if (!imports.isEmpty()) {
                imports.stream()
                        .filter(i -> !i.startsWith("java.lang.") && !inSamePackage(i))
                        .sorted().forEach(i ->
                        sb.append("import ")
                                .append(i)
                                .append(";\n"));
                sb.append("\n");
            }
        }
        Set<TypeInfo> annotationsSeen = new HashSet<>();
        for (AnnotationExpression annotation : annotations) {
            StringUtil.indent(sb, indent);
            sb.append(annotation.stream());
            if (typeAnalysis.isSet()) {
                typeAnalysis.get().peekIntoAnnotations(annotation, annotationsSeen, sb);
            }
            sb.append("\n");
        }
        if (typeAnalysis.isSet()) {
            typeAnalysis.get().annotations.visit((annotation, present) -> {
                if (present && !annotationsSeen.contains(annotation.typeInfo)) {
                    StringUtil.indent(sb, indent);
                    sb.append(annotation.stream());
                    sb.append("\n");
                }
            });
        }
        if (doTypeDeclaration) {
            // the class name
            StringUtil.indent(sb, indent);
            sb.append(typeModifiers.map(s -> s + " ").collect(Collectors.joining()));
            sb.append(typeNature);
            sb.append(" ");
            sb.append(simpleName);
            if (!typeParametersCsv.isEmpty()) {
                sb.append("<");
                sb.append(typeParametersCsv);
                sb.append(">");
            }
            if (!parentClass.isEmpty()) {
                sb.append(" extends ");
                sb.append(parentClass);
            }
            if (!interfacesCsv.isEmpty()) {
                sb.append(" implements ");
                sb.append(interfacesCsv);
            }
        }
        sb.append(" {\n\n");

        // these already have indentation built in
        niceStream(sb, subTypesStream, "\n\n", "");
        niceStream(sb, fieldsStream, "\n", "\n");
        niceStream(sb, constructorsStream, "\n\n", "\n");
        niceStream(sb, methodsStream, "\n\n", "\n");

        StringUtil.indent(sb, indent);
        sb.append("}\n");
        return sb.toString();
    }

    private boolean inSamePackage(String i) {
        // TODO this should be done better
        int lastDot = i.lastIndexOf('.');
        if (lastDot > 0 && hasBeenInspected() && typeInspection.getPotentiallyRun().packageNameOrEnclosingType.isLeft()) {
            return typeInspection.getPotentiallyRun().packageNameOrEnclosingType.getLeft().equals(i.substring(0, lastDot));
        }
        return false;
    }

    private static void niceStream(StringBuilder sb, Stream<String> stream, String separator, String suffix) {
        AtomicInteger cnt = new AtomicInteger();
        stream.forEach(s -> {
            sb.append(s);
            sb.append(separator);
            cnt.incrementAndGet();
        });
        if (cnt.get() > 0)
            sb.append(suffix);
    }

    public boolean isPrimitive() {
        return PRIMITIVES.primitives.contains(this);
    }

    public boolean isPrimitiveOrBoxed() {
        return isPrimitive() || PRIMITIVES.boxed.contains(this);
    }

    public boolean isNumericPrimitive() {
        return PRIMITIVES.numericPrimitives.contains(this);
    }

    public boolean isNumericPrimitiveBoxed() {
        return isNumericPrimitive() || PRIMITIVES.numericBoxed.contains(this);
    }

    public boolean isJavaLang() {
        if (isPrimitive()) return true;
        return fullyQualifiedName.startsWith(Primitives.JAVA_LANG);
    }

    public Set<String> imports() {
        Set<String> imports = new HashSet<>();

        // explicitly adding this to allow for computed annotations, which will happen all the time
        imports.add(AnnotationType.class.getCanonicalName());
        if (hasBeenInspected()) {
            for (AnnotationExpression annotation : typeInspection.getPotentiallyRun().annotations) {
                imports.add(annotation.typeInfo.fullyQualifiedName);
            }
            for (TypeInfo subType : typeInspection.getPotentiallyRun().subTypes) {
                imports.addAll(subType.imports());
            }
            for (MethodInfo methodInfo : typeInspection.getPotentiallyRun().methodsAndConstructors()) {
                imports.addAll(methodInfo.imports());
            }
            for (FieldInfo fieldInfo : typeInspection.getPotentiallyRun().fields) {
                imports.addAll(fieldInfo.imports());
            }
        }
        return imports;
    }

    /**
     * This is the starting place to compute all types that are referred to in any way.
     * It is different from imports, because imports need an explicitly written type.
     *
     * @return a set of all types referenced
     */
    public Set<TypeInfo> typesReferenced() {
        Set<TypeInfo> result = new HashSet<>();
        result.add(this);

        for (AnnotationExpression annotation : typeInspection.getPotentiallyRun().annotations) {
            result.add(annotation.typeInfo);
        }
        for (TypeInfo subType : typeInspection.getPotentiallyRun().subTypes) {
            result.addAll(subType.typesReferenced());
        }
        for (MethodInfo methodInfo : typeInspection.getPotentiallyRun().methodsAndConstructors()) {
            result.addAll(methodInfo.typesReferenced());
        }
        for (FieldInfo fieldInfo : typeInspection.getPotentiallyRun().fields) {
            result.addAll(fieldInfo.typesReferenced());
        }
        return result;
    }

    @Override
    public String simpleName() {
        return simpleName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TypeInfo typeInfo = (TypeInfo) o;
        return fullyQualifiedName.equals(typeInfo.fullyQualifiedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fullyQualifiedName);
    }

    @Override
    public Optional<AnnotationExpression> hasTestAnnotation(Class<?> annotation) {
        if (!hasBeenDefined()) return Optional.empty();
        String annotationFQN = annotation.getName();
        Optional<AnnotationExpression> fromType = (getInspection().annotations.stream()
                .filter(ae -> ae.typeInfo.fullyQualifiedName.equals(annotationFQN)))
                .findFirst();
        if (fromType.isPresent()) return fromType;
        if (typeInspection.getPotentiallyRun().parentClass != ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT) {
            Optional<AnnotationExpression> fromParent = typeInspection.getPotentiallyRun().parentClass.typeInfo.hasTestAnnotation(annotation);
            if (fromParent.isPresent()) return fromParent;
        }
        return Optional.empty();
    }

    public Optional<TypeInfo> inTypeInnerOuterHierarchy(TypeInfo typeInfo) {
        return inTypeInnerOuterHierarchy(typeInfo, new HashSet<>());
    }

    private Optional<TypeInfo> inTypeInnerOuterHierarchy(TypeInfo typeInfo, Set<TypeInfo> visited) {
        if (typeInfo == this) return Optional.of(this);
        if (visited.contains(this)) return Optional.empty();
        visited.add(this);
        if (typeInspection.getPotentiallyRun().packageNameOrEnclosingType.isRight()) {
            TypeInfo parentClass = typeInspection.getPotentiallyRun().packageNameOrEnclosingType.getRight();
            Optional<TypeInfo> viaParent = parentClass.inTypeInnerOuterHierarchy(typeInfo, visited);
            if (viaParent.isPresent()) return viaParent;
        }
        for (TypeInfo subType : typeInspection.getPotentiallyRun().subTypes) {
            Optional<TypeInfo> viaSubType = subType.inTypeInnerOuterHierarchy(typeInfo, visited);
            if (viaSubType.isPresent()) return viaSubType;
        }
        return Optional.empty();
    }

    public ParameterizedType asParameterizedType() {
        if (!typeInspection.isSetPotentiallyRun()) {
            return new ParameterizedType(this, List.of());
        }
        return new ParameterizedType(this, typeInspection.getPotentiallyRun().typeParameters
                .stream().map(tp -> new ParameterizedType(tp, 0, ParameterizedType.WildCard.NONE)).collect(Collectors.toList()));
    }

    public boolean isStatic() {
        if (!typeInspection.isSetPotentiallyRun()) throw new UnsupportedOperationException();
        if (typeInspection.getPotentiallyRun().packageNameOrEnclosingType.isLeft()) return true; // independent type
        return typeInspection.getPotentiallyRun().modifiers.contains(TypeModifier.STATIC); // static sub type
    }

    /**
     * Find a method, given a translation map
     *
     * @param target         the method to find (typically from a sub type)
     * @param translationMap from the type parameters of this to the concrete types of the sub-type
     * @return the method of this, if deemed the same
     */
    private MethodInfo findUniqueMethod(MethodInfo target, Map<NamedType, ParameterizedType> translationMap) {
        for (MethodInfo methodInfo : typeInspection.getPotentiallyRun(fullyQualifiedName).methodsAndConstructors()) {
            if (methodInfo.sameMethod(target, translationMap)) {
                return methodInfo;
            }
        }
        return null;
    }

    public List<ParameterizedType> directSuperTypes() {
        if (Primitives.JAVA_LANG_OBJECT.equals(fullyQualifiedName)) return List.of();
        List<ParameterizedType> list = new ArrayList<>();
        ParameterizedType parentPt = typeInspection.getPotentiallyRun().parentClass;
        boolean parentIsJLO = parentPt == ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT;
        ParameterizedType parent;
        if (parentIsJLO) {
            parent = Objects.requireNonNull(PRIMITIVES.objectParameterizedType);
        } else {
            parent = Objects.requireNonNull(parentPt);
        }
        list.add(parent);
        list.addAll(typeInspection.getPotentiallyRun().interfacesImplemented);
        return list;
    }

    public List<TypeInfo> superTypes() {
        if (Primitives.JAVA_LANG_OBJECT.equals(fullyQualifiedName)) return List.of();
        if (typeInspection.getPotentiallyRun().superTypes.isSet()) return typeInspection.getPotentiallyRun().superTypes.get();
        List<TypeInfo> list = new ArrayList<>();
        ParameterizedType parentPt = typeInspection.getPotentiallyRun().parentClass;
        TypeInfo parent;
        boolean parentIsJLO = parentPt == ParameterizedType.IMPLICITLY_JAVA_LANG_OBJECT;
        if (parentIsJLO) {
            parent = Objects.requireNonNull(PRIMITIVES.objectTypeInfo);
            if (typeInspection.getPotentiallyRun().isClass()) {
                list.add(parent);
            }
        } else {
            parent = Objects.requireNonNull(parentPt.typeInfo);
            list.add(parent);
            list.addAll(parent.superTypes());
        }

        typeInspection.getPotentiallyRun().interfacesImplemented.forEach(i -> {
            list.add(i.typeInfo);
            list.addAll(i.typeInfo.superTypes());
        });
        List<TypeInfo> immutable = ImmutableList.copyOf(list);
        typeInspection.getPotentiallyRun().superTypes.set(immutable);
        return immutable;
    }

    /**
     * What does it do: look into my super types, and see if you find a method like the one specified
     * NOTE: it does not look "sideways: methods of the same type but where implicit type conversion can take place
     *
     * @param methodInfo: the method for which we're looking for overrides
     * @return all super methods
     */
    public Set<MethodInfo> overrides(MethodInfo methodInfo, boolean cacheResult) {
        // NOTE: we cache, but only at our own level
        boolean ourOwnLevel = methodInfo.typeInfo == this;
        if (cacheResult) {
            Set<MethodInfo> myOverrides = ourOwnLevel ? typeInspection.getPotentiallyRun().overrides.getOtherwiseNull(methodInfo) : null;
            if (myOverrides != null) return myOverrides;
        }
        Set<MethodInfo> result = recursiveOverridesCall(methodInfo, Map.of());
        Set<MethodInfo> immutable = ImmutableSet.copyOf(result);
        if (ourOwnLevel && cacheResult) {
            typeInspection.getPotentiallyRun().overrides.put(methodInfo, immutable);
        }
        return immutable;
    }

    private Set<MethodInfo> recursiveOverridesCall(MethodInfo methodInfo, Map<NamedType, ParameterizedType> translationMap) {
        Set<MethodInfo> result = new HashSet<>();
        for (ParameterizedType superType : directSuperTypes()) {
            Map<NamedType, ParameterizedType> translationMapOfSuperType;
            if (superType.parameters.isEmpty()) {
                translationMapOfSuperType = translationMap;
            } else {
                ParameterizedType formalType = superType.typeInfo.asParameterizedType();
                translationMapOfSuperType = new HashMap<>(translationMap);
                int index = 0;
                for (ParameterizedType parameter : formalType.parameters) {
                    ParameterizedType concreteParameter = superType.parameters.get(index);
                    translationMapOfSuperType.put(parameter.typeParameter, concreteParameter);
                    index++;
                }
            }
            MethodInfo override = superType.typeInfo.findUniqueMethod(methodInfo, translationMapOfSuperType);
            if (override != null) {
                result.add(override);
            }
            if (superType.typeInfo != PRIMITIVES.objectTypeInfo) {
                result.addAll(superType.typeInfo.recursiveOverridesCall(methodInfo, translationMapOfSuperType));
            }
        }
        return result;
    }

    @Override
    public String toString() {
        return fullyQualifiedName;
    }

    public MethodInfo getMethodOrConstructorByDistinguishingName(String distinguishingName) {
        return typeInspection.getPotentiallyRun().constructorAndMethodStream(TypeInspection.Methods.EXCLUDE_FIELD_SAM)
                .filter(methodInfo -> methodInfo.distinguishingName().equals(distinguishingName))
                .findFirst().orElse(null);
    }

    public FieldInfo getFieldByName(String name) {
        return typeInspection.getPotentiallyRun().fields.stream().filter(fieldInfo -> fieldInfo.name.equals(name)).findFirst().orElse(null);
    }

    public TypeInfo primaryType() {
        if (typeInspection.isSetPotentiallyRun()) {
            Either<String, TypeInfo> packageNameOrEnclosingType = typeInspection.getPotentiallyRun().packageNameOrEnclosingType;
            if (packageNameOrEnclosingType.isLeft()) return this;
            return packageNameOrEnclosingType.getRight().primaryType();
        }
        throw new UnsupportedOperationException("Type inspection on " + fullyQualifiedName + " not yet set");
    }

    /*

    Function<String, Integer> f = Type::someFunction;

    gets converted into

    Function<String, Integer> f2 = new Function<String, Integer>() {
        @Override
        public Integer apply(String s) {
            return Type.someFunction(s);
        }
    };

     */

    public MethodInfo convertMethodReferenceIntoLambda(ParameterizedType functionalInterfaceType,
                                                       TypeInfo enclosingType,
                                                       MethodReference methodReference,
                                                       ExpressionContext expressionContext,
                                                       Resolver resolver) {
        MethodTypeParameterMap method = functionalInterfaceType.findSingleAbstractMethodOfInterface();
        TypeInfo typeInfo = new TypeInfo(enclosingType, expressionContext.topLevel.newIndex(enclosingType));
        TypeInspection.TypeInspectionBuilder builder = new TypeInspection.TypeInspectionBuilder();
        builder.setEnclosingType(this);
        builder.setTypeNature(TypeNature.CLASS);
        builder.addInterfaceImplemented(functionalInterfaceType);

        // there are no extra type parameters; only those of the enclosing type(s) can be in 'type'

        MethodInfo methodInfo = method.buildCopy(typeInfo);
        builder.addMethod(methodInfo);

        // compose the content of the method...

        Expression newReturnExpression;
        if (methodReference.methodInfo.isStatic || !(methodReference.scope instanceof TypeExpression)) {
            newReturnExpression = methodCallCopyAllParameters(methodReference.scope, methodReference.methodInfo, methodInfo);
        } else {
            if (methodInfo.methodInspection.get().parameters.size() != 1)
                throw new UnsupportedOperationException("Referenced method has multiple parameters");
            newReturnExpression = methodCallNoParameters(methodInfo, methodReference.methodInfo);
        }
        Statement statement;
        if (methodInfo.isVoid()) {
            statement = new ExpressionAsStatement(newReturnExpression);
        } else {
            statement = new ReturnStatement(newReturnExpression);
        }
        Block block = new Block.BlockBuilder().addStatement(statement).build();

        log(LAMBDA, "Result of translating block: {}", block.statementString(0, null));
        methodInfo.methodInspection.get().methodBody.set(block);
        typeInfo.typeInspection.set(builder.build(true, typeInfo));

        resolver.sortTypes(Map.of(typeInfo, expressionContext.typeContext), expressionContext.e2ImmuAnnotationExpressions);
        ParseLambdaExpr.ensureLambdaAnalysisDefaults(typeInfo);

        return methodInfo;
    }

    private Expression methodCallNoParameters(MethodInfo interfaceMethod, MethodInfo concreteMethod) {
        Expression newScope = new VariableExpression(interfaceMethod.methodInspection.get().parameters.get(0));
        MethodTypeParameterMap methodTypeParameterMap = new MethodTypeParameterMap(concreteMethod, Map.of());
        return new MethodCall(newScope, newScope, methodTypeParameterMap, List.of());
    }

    private Expression methodCallCopyAllParameters(Expression scope, MethodInfo concreteMethod, MethodInfo interfaceMethod) {
        List<Expression> parameterExpressions = interfaceMethod.methodInspection.get()
                .parameters.stream().map(VariableExpression::new).collect(Collectors.toList());
        Map<NamedType, ParameterizedType> concreteTypes = new HashMap<>();
        int i = 0;
        for (ParameterInfo parameterInfo : concreteMethod.methodInspection.get().parameters) {
            ParameterInfo interfaceParameter = interfaceMethod.methodInspection.get().parameters.get(i);
            if (interfaceParameter.parameterizedType.isTypeParameter()) {
                concreteTypes.put(interfaceParameter.parameterizedType.typeParameter, parameterInfo.parameterizedType);
            }
            i++;
        }
        MethodTypeParameterMap methodTypeParameterMap = new MethodTypeParameterMap(concreteMethod, concreteTypes);
        return new MethodCall(scope, scope, methodTypeParameterMap, parameterExpressions);
    }

    public boolean isNestedType() {
        return typeInspection.getPotentiallyRun().packageNameOrEnclosingType.isRight();
    }

    public List<TypeInfo> allTypesInPrimaryType() {
        return primaryType().typeInspection.getPotentiallyRun().allTypesInPrimaryType;
    }

    public boolean isPrivate() {
        return typeInspection.getPotentiallyRun().modifiers.contains(TypeModifier.PRIVATE);
    }

    public boolean isAnEnclosingTypeOf(TypeInfo typeInfo) {
        if (typeInfo == this) return true;
        if (typeInfo.typeInspection.getPotentiallyRun().packageNameOrEnclosingType.isLeft()) return false;
        return isAnEnclosingTypeOf(typeInfo.typeInspection.getPotentiallyRun().packageNameOrEnclosingType.getRight());
    }

    public List<TypeInfo> myselfAndMyEnclosingTypes() {
        if (isNestedType()) {
            return ListUtil.immutableConcat(List.of(this), typeInspection.getPotentiallyRun().packageNameOrEnclosingType
                    .getRight().myselfAndMyEnclosingTypes());
        }
        return List.of(this);
    }

    public boolean isRecord() {
        return isNestedType() && isPrivate();
    }

    public Messages copyAnnotationsIntoTypeAnalysisProperties(E2ImmuAnnotationExpressions typeContext, boolean overwrite, String where) {
        boolean acceptVerify = !hasBeenDefined() || isInterface();
        log(RESOLVE, "{}: copy annotations into properties: {}", where, fullyQualifiedName);
        Messages messages = new Messages();
        if (this.typeAnalysis.isSet()) {
            if (!overwrite)
                throw new UnsupportedOperationException("Type analysis already set for " + fullyQualifiedName);
        } else {
            TypeAnalysis typeAnalysis = new TypeAnalysis(this);
            this.typeAnalysis.set(typeAnalysis);
        }
        messages.addAll(typeAnalysis.get().fromAnnotationsIntoProperties(acceptVerify, typeInspection.getPotentiallyRun().annotations, typeContext, overwrite));
        typeInspection.getPotentiallyRun().methodsAndConstructors().forEach(methodInfo ->
                messages.addAll(methodInfo.copyAnnotationsIntoMethodAnalysisProperties(typeContext, overwrite)));
        typeInspection.getPotentiallyRun().fields.forEach(fieldInfo ->
                messages.addAll(fieldInfo.copyAnnotationsIntoFieldAnalysisProperties(typeContext, overwrite)));
        return messages;
    }

    public void resolveAllAnnotations(ExpressionContext expressionContext) {
        typeInspection.getPotentiallyRun().annotations.stream().filter(ae -> !ae.expressions.isSet())
                .forEach(ae -> ae.resolve(expressionContext));
        typeInspection.getPotentiallyRun().subTypes.forEach(subType -> subType.resolveAllAnnotations(expressionContext));
        typeInspection.getPotentiallyRun().methodsAndConstructors().forEach(methodInfo -> {
            methodInfo.methodInspection.get().annotations.stream().filter(ae -> !ae.expressions.isSet())
                    .forEach(ae -> ae.resolve(expressionContext));
            methodInfo.methodInspection.get().parameters.forEach(parameterInfo ->
                    parameterInfo.parameterInspection.get().annotations
                            .stream().filter(ae -> !ae.expressions.isSet())
                            .forEach(ae -> ae.resolve(expressionContext)));
        });
        typeInspection.getPotentiallyRun().fields.forEach(fieldInfo ->
                fieldInfo.fieldInspection.get().annotations.stream().filter(ae -> !ae.expressions.isSet())
                        .forEach(ae -> ae.resolve(expressionContext)));
    }

    public boolean isInterface() {
        return typeInspection.getPotentiallyRun().typeNature == TypeNature.INTERFACE;
    }

    public boolean isFunctionalInterface() {
        if (typeInspection.getPotentiallyRun("isFunctionalInterface on " + fullyQualifiedName).typeNature != TypeNature.INTERFACE) {
            return false;
        }
        return typeInspection.getPotentiallyRun().annotations.contains(PRIMITIVES.functionalInterfaceAnnotationExpression);
    }

    public MethodInfo sizeMethod() {
        MethodInfo methodInfo = typeInspection.getPotentiallyRun().methodStream(TypeInspection.Methods.EXCLUDE_FIELD_SAM)
                .filter(mi -> returnsIntOrLong(mi) && mi.methodInspection.get().parameters.isEmpty())
                .filter(mi -> mi.getAnalysis().getProperty(VariableProperty.SIZE) > Level.FALSE)
                .filter(mi -> mi.methodAnalysis.get().getProperty(VariableProperty.MODIFIED) == Level.FALSE)
                .findFirst().orElse(null);
        if (methodInfo != null) {
            return methodInfo;
        }
        return superTypes().stream().map(TypeInfo::sizeMethod).filter(Objects::nonNull).findFirst().orElse(null);
    }

    public boolean hasSize() {
        return sizeMethod() != null;
    }

    public static boolean returnsIntOrLong(MethodInfo methodInfo) {
        TypeInfo returnType = methodInfo.returnType().typeInfo;
        return returnType == PRIMITIVES.integerTypeInfo || returnType == PRIMITIVES.intTypeInfo ||
                returnType == PRIMITIVES.longTypeInfo || returnType == PRIMITIVES.boxedLongTypeInfo;
    }

    public Set<ObjectFlow> objectFlows() {
        Set<ObjectFlow> result = typeAnalysis.get().getConstantObjectFlows().collect(Collectors.toCollection(HashSet::new));
        for (MethodInfo methodInfo : typeInspection.getPotentiallyRun().methodsAndConstructors()) {
            // set, because the returned object flow could equal either one of the non-returned, or parameter flows
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                result.add(parameterInfo.parameterAnalysis.get().getObjectFlow());
            }
            MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
            result.addAll(methodAnalysis.internalObjectFlows.get());

            if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                result.add(methodAnalysis.getObjectFlow());
            }
        }
        // for fields we only add those owned by the field itself (i.e. with an initialiser)
        for (FieldInfo fieldInfo : typeInspection.getPotentiallyRun().fields) {
            ObjectFlow objectFlow = fieldInfo.fieldAnalysis.get().getObjectFlow();
            if (objectFlow != null && objectFlow.location.info == fieldInfo) {
                result.add(objectFlow);
            }
            result.addAll(fieldInfo.fieldAnalysis.get().internalObjectFlows.get());
        }
        for (TypeInfo subType : typeInspection.getPotentiallyRun().subTypes) {
            result.addAll(subType.objectFlows());
        }
        return result;
    }

    @Override
    public String detailedName() {
        return fullyQualifiedName;
    }

    public boolean isEventual() {
        return typeAnalysis.get().isEventual();
    }

    public MethodInfo findUniqueMethod(String methodName, int parameters) {
        return typeInspection.getPotentiallyRun().methodStream(TypeInspection.Methods.EXCLUDE_FIELD_SAM).
                filter(m -> m.name.equals(methodName) && m.methodInspection.get().parameters.size() == parameters)
                .findAny().orElseThrow();
    }

    public Set<ParameterizedType> typesOfMethodsAndConstructors() {
        Set<ParameterizedType> result = new HashSet<>();
        for (MethodInfo methodInfo : typeInspection.getPotentiallyRun().methodsAndConstructors()) {
            if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                result.add(methodInfo.returnType());
            }
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().parameters) {
                result.add(parameterInfo.parameterizedType);
            }
        }
        return result;
    }

    public String packageName() {
        if (!typeInspection.isSet()) return null;
        if (typeInspection.getPotentiallyRun().packageNameOrEnclosingType.isLeft())
            return typeInspection.getPotentiallyRun().packageNameOrEnclosingType.getLeft();
        return typeInspection.getPotentiallyRun().packageNameOrEnclosingType.getRight().packageName();
    }

    // this type implements a functional interface, and we need to find the single abstract method
    public MethodInfo findOverriddenSingleAbstractMethod() {
        return typeInspection.getPotentiallyRun().methodStream(TypeInspection.Methods.EXCLUDE_FIELD_SAM)
                .filter(mi -> !mi.isDefaultImplementation && !mi.isStatic)
                .findFirst().orElseThrow();
    }

    public Set<ParameterizedType> explicitTypes() {
        // handles SAMs of fields as well
        Stream<ParameterizedType> methods = typeInspection.getPotentiallyRun().constructorAndMethodStream(TypeInspection.Methods.ALL)
                .flatMap(methodInfo -> methodInfo.explicitTypes().stream());
        Stream<ParameterizedType> fields = typeInspection.getPotentiallyRun().fields.stream().flatMap(fieldInfo -> fieldInfo.explicitTypes().stream());
        return Stream.concat(methods, fields).collect(Collectors.toSet());
    }

    public MethodInfo findConstructor(int parameters) {
        return typeInspection.getPotentiallyRun().constructors.stream()
                .filter(c -> c.methodInspection.get().parameters.size() == parameters)
                .findFirst().orElseThrow();
    }
}
