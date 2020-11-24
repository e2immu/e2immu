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
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.INSPECT;
import static org.e2immu.analyser.util.Logger.log;

/*
 Overall structure of an "Annotated API" Java class:

import com.google.common.collect.ImmutableSet;                    // real imports are possible

public class ComGoogleCommonCollect {                             // name irrelevant
    final static String PACKAGE_NAME="com.google.common.collect"; // field must have exactly this name

    static class ImmutableCollection$<E> {                        // the $ changes the package to the content of PACKAGE_NAME
        ImmutableList<E> asList() { return null;  }               // body of method completely ignored

        boolean size$Invariant$Size(int i) { return i >= 0; }     // companion methods must precede the method directly
        void size$Aspect$Size() {}
        @NotModified                                              // annotations are added
        int size() { return 0; }

        int toArray$Transfer$Size(int size) { return size; }
        public Object[] toArray() { return null; }
    }
    ...
}

It is important to note that the type inspector first *adds* to the byte-code inspection of ImmutableCollection,
as the Annotated API Java class may not be complete. In this case fullInspection is false.

 */

public class TypeInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeInspector.class);
    public static final String PACKAGE_NAME_FIELD = "PACKAGE_NAME";

    private final TypeInfo typeInfo;
    private final TypeInspectionImpl.Builder builder;
    private final boolean fullInspection;

    public TypeInspector(TypeMapImpl.Builder typeMapBuilder, TypeInfo typeInfo, boolean fullInspection) {
        this.typeInfo = typeInfo;

        TypeInspection typeInspection = typeMapBuilder.getTypeInspection(typeInfo);
        if (typeInspection == null || typeInspection.getInspectionState() >= TypeInspectionImpl.FINISHED_JAVA_PARSER) {
            throw new UnsupportedOperationException();
        }
        this.fullInspection = fullInspection;
        builder = (TypeInspectionImpl.Builder) typeInspection;
        builder.setInspectionState(TypeInspectionImpl.STARTING_JAVA_PARSER);
    }

    public TypeInspection build() {
        return builder.build();
    }

    public void inspectAnonymousType(ParameterizedType classImplemented,
                                     ExpressionContext expressionContext,
                                     NodeList<BodyDeclaration<?>> members) {
        assert fullInspection; // no way we could reach this otherwise
        builder.setEnclosingType(expressionContext.enclosingType);
        builder.setParentClass(expressionContext.typeContext.getPrimitives().objectParameterizedType);
        assert classImplemented.typeInfo != null && classImplemented.typeInfo.hasBeenInspected();
        if (classImplemented.typeInfo.typeInspection.get().typeNature() == TypeNature.INTERFACE) {
            builder.addInterfaceImplemented(classImplemented);
        } else {
            builder.setParentClass(classImplemented);
        }
        continueInspection(expressionContext, members, false, false, null);
    }

    /**
     * @param enclosingTypeIsInterface when true, the enclosing type is an interface, and we need to add PUBLIC
     * @param enclosingType            when not null, denotes the parent type; otherwise, this is a primary type
     * @param typeDeclaration          the JavaParser object to inspect
     * @param expressionContext        the context to inspect in
     */
    public List<TypeInfo> inspect(boolean enclosingTypeIsInterface,
                                  TypeInfo enclosingType,
                                  TypeDeclaration<?> typeDeclaration,
                                  ExpressionContext expressionContext) {
        List<TypeInfo> dollarTypes = inspect(enclosingTypeIsInterface, enclosingType, typeDeclaration, expressionContext, null);
        if (enclosingType == null) {
            dollarTypes.add(0, typeInfo);
        }
        return dollarTypes;
    }

    /*
    returns the primary type in normal cases, and primary types in case of $ types
     */
    private List<TypeInfo> inspect(boolean enclosingTypeIsInterface,
                                   TypeInfo enclosingType,
                                   TypeDeclaration<?> typeDeclaration,
                                   ExpressionContext expressionContext,
                                   DollarResolver dollarResolverInput) {
        LOGGER.info("Inspecting type {}", typeInfo.fullyQualifiedName);

        DollarResolver dollarResolver;
        if (enclosingType != null) {
            builder.setEnclosingType(enclosingType);
            dollarResolver = dollarResolverInput;
        } else {
            builder.setPackageName(typeInfo.computePackageName());
            FieldDeclaration packageNameField = typeDeclaration.getFieldByName(PACKAGE_NAME_FIELD).orElse(null);
            String dollarPackageName = packageName(packageNameField);
            dollarResolver = name -> {
                if (name.endsWith("$") && dollarPackageName != null) {
                    return expressionContext.typeContext.typeMapBuilder.get(dollarPackageName + "." + name.substring(0, name.length() - 1));
                }
                return null;
            };
        }
        if (fullInspection) {
            builder.setParentClass(expressionContext.typeContext.getPrimitives().objectParameterizedType);
            TypeNature typeNature = typeNature(typeDeclaration);
            builder.setTypeNature(typeNature);
            if (enclosingTypeIsInterface) {
                builder.addTypeModifier(TypeModifier.PUBLIC);
                if (typeNature == TypeNature.INTERFACE) {
                    builder.addTypeModifier(TypeModifier.STATIC);
                }
            }
        }
        expressionContext.typeContext.addToContext(typeInfo);


        boolean haveFunctionalInterface = false;
        for (AnnotationExpr annotationExpr : typeDeclaration.getAnnotations()) {
            AnnotationExpression ae = AnnotationExpressionImpl.inspect(expressionContext, annotationExpr);
            haveFunctionalInterface |= "java.lang.FunctionalInterface".equals(ae.typeInfo().fullyQualifiedName);
            builder.addAnnotation(ae);
        }
        if (fullInspection) {
            if (typeDeclaration instanceof EnumDeclaration) {
                doEnumDeclaration(expressionContext, (EnumDeclaration) typeDeclaration);
            }
            if (typeDeclaration instanceof AnnotationDeclaration) {
                doAnnotationDeclaration(expressionContext, (AnnotationDeclaration) typeDeclaration);
            }
            if (typeDeclaration instanceof ClassOrInterfaceDeclaration) {
                doClassOrInterfaceDeclaration(expressionContext, (ClassOrInterfaceDeclaration) typeDeclaration);
            }

            for (Modifier modifier : typeDeclaration.getModifiers()) {
                builder.addTypeModifier(TypeModifier.from(modifier));
            }
        }
        return continueInspection(expressionContext, typeDeclaration.getMembers(),
                builder.typeNature() == TypeNature.INTERFACE, haveFunctionalInterface, dollarResolver);
    }

    private void doAnnotationDeclaration(ExpressionContext expressionContext, AnnotationDeclaration annotationDeclaration) {
        builder.setTypeNature(TypeNature.ANNOTATION);
        ExpressionContext subContext = expressionContext.newVariableContext("annotation body of " + typeInfo.fullyQualifiedName);

        for (BodyDeclaration<?> bd : annotationDeclaration.getMembers()) {
            if (bd.isAnnotationMemberDeclaration()) {
                AnnotationMemberDeclaration amd = bd.asAnnotationMemberDeclaration();
                log(INSPECT, "Have member {} in {}", amd.getNameAsString(), typeInfo.fullyQualifiedName);
                String methodName = amd.getName().getIdentifier();
                MethodInfo methodInfo = new MethodInfo(typeInfo, methodName, List.of(),
                        expressionContext.typeContext.getPrimitives().voidParameterizedType, true, true);
                MethodInspector methodInspector = new MethodInspector(expressionContext.typeContext.typeMapBuilder, methodInfo, fullInspection);
                methodInspector.inspect(amd, subContext);
                builder.addMethod(methodInfo);
            }
        }
    }

    private void doEnumDeclaration(ExpressionContext expressionContext, EnumDeclaration enumDeclaration) {
        builder.setTypeNature(TypeNature.ENUM);
        enumDeclaration.getEntries().forEach(enumConstantDeclaration -> {
            FieldInfo fieldInfo = new FieldInfo(typeInfo, enumConstantDeclaration.getNameAsString(), typeInfo);
            FieldInspectionImpl.Builder fieldBuilder = new FieldInspectionImpl.Builder();
            fieldBuilder.addModifier(FieldModifier.FINAL);
            fieldBuilder.addModifier(FieldModifier.PUBLIC);
            fieldBuilder.addModifier(FieldModifier.STATIC);
            fieldInfo.fieldInspection.set(fieldBuilder.build());
            builder.addField(fieldInfo);
            // TODO we have arguments, class body
        });
        Primitives primitives = expressionContext.typeContext.getPrimitives();
        MethodInfo nameMethodInfo = new MethodInfo(typeInfo, "name", List.of(),
                primitives.stringParameterizedType, false);
        E2ImmuAnnotationExpressions e2 = expressionContext.typeContext.typeMapBuilder.getE2ImmuAnnotationExpressions();
        nameMethodInfo.methodInspection.set(new MethodInspectionImpl.Builder(nameMethodInfo)
                .addAnnotation(e2.notModified)
                .setReturnType(primitives.stringParameterizedType)
                .build());

        MethodInfo valueOfMethodInfo = new MethodInfo(typeInfo, "valueOf", List.of(),
                primitives.stringParameterizedType, true);
        MethodInspectionImpl.Builder valueOfBuilder = new MethodInspectionImpl.Builder(valueOfMethodInfo)
                .addAnnotation(e2.notModified).setReturnType(typeInfo.asParameterizedType());

        ParameterInfo valueOfP0 = new ParameterInfo(valueOfMethodInfo, primitives.stringParameterizedType, "name", 0);
        ParameterInspectionImpl.Builder valueOfP0B = valueOfBuilder.addParameterCreateBuilder(valueOfP0);
        valueOfP0B.addAnnotation(e2.notNull);

        valueOfMethodInfo.methodInspection.set(valueOfBuilder.build());

        builder.addMethod(nameMethodInfo).addMethod(valueOfMethodInfo);
    }

    private void doClassOrInterfaceDeclaration(ExpressionContext expressionContext, ClassOrInterfaceDeclaration cid) {
        int tpIndex = 0;
        for (com.github.javaparser.ast.type.TypeParameter typeParameter : cid.getTypeParameters()) {
            TypeParameter tp = new TypeParameter(typeInfo, typeParameter.getNameAsString(), tpIndex++);
            expressionContext.typeContext.addToContext(tp);
            tp.inspect(expressionContext.typeContext, typeParameter);
            builder.addTypeParameter(tp);
        }
        if (builder.typeNature() == TypeNature.CLASS) {
            if (!cid.getExtendedTypes().isEmpty()) {
                ParameterizedType parameterizedType = ParameterizedType.from(expressionContext.typeContext, cid.getExtendedTypes(0));
                // why this check? hasBeenDefined == true signifies Java parsing; == false is annotated APIs.
                // the annotated APIs are backed by .class files, which can be inspected with byte code; there, we only have
                // fully qualified names. In Java, we must add type names of parent's subtypes etc.
                if (fullInspection) ensureLoaded(expressionContext, parameterizedType);
                builder.setParentClass(parameterizedType);
            }
            for (ClassOrInterfaceType extended : cid.getImplementedTypes()) {
                ParameterizedType parameterizedType = ParameterizedType.from(expressionContext.typeContext, extended);
                if (fullInspection) ensureLoaded(expressionContext, parameterizedType);
                builder.addInterfaceImplemented(parameterizedType);
            }
        } else {
            if (builder.typeNature() != TypeNature.INTERFACE) throw new UnsupportedOperationException();
            for (ClassOrInterfaceType extended : cid.getExtendedTypes()) {
                ParameterizedType parameterizedType = ParameterizedType.from(expressionContext.typeContext, extended);
                if (fullInspection) ensureLoaded(expressionContext, parameterizedType);
                builder.addInterfaceImplemented(parameterizedType);
            }
        }
    }

    /**
     * calling "get" on the typeInspection of the parameterizedType will trigger recursive parsing.
     * But we should not do that when we're inside the same compilation unit: the primary type and all its subtypes.
     */
    private void ensureLoaded(ExpressionContext expressionContext, ParameterizedType parameterizedType) {
        assert parameterizedType.typeInfo != null;
        boolean insideCompilationUnit = parameterizedType.typeInfo.fullyQualifiedName.startsWith(expressionContext.primaryType.fullyQualifiedName);
        if (!insideCompilationUnit) {
            InspectionProvider inspectionProvider = expressionContext.typeContext.typeMapBuilder;
            // getting the type inspection should trigger either byte-code or java inspection
            TypeInspection typeInspection = inspectionProvider.getTypeInspection(parameterizedType.typeInfo);
            if (typeInspection == null) {
                throw new UnsupportedOperationException("The type " + parameterizedType.typeInfo.fullyQualifiedName + " should already have a type inspection");
            }
            // now that we're sure it has been inspected, we add all its top-level subtypes to the type context
            for (TypeInfo subType : typeInspection.subTypes()) {
                expressionContext.typeContext.addToContext(subType);
            }
            if (!Primitives.isJavaLangObject(typeInspection.parentClass())) {
                ensureLoaded(expressionContext, typeInspection.parentClass());
            }
            typeInspection.interfacesImplemented().forEach(i -> ensureLoaded(expressionContext, i));
        }
    }

    public void inspectLocalClassDeclaration(ExpressionContext expressionContext, TypeInfo localType, ClassOrInterfaceDeclaration cid) {
        TypeInspectionImpl.Builder builder = new TypeInspectionImpl.Builder(localType, TypeInspectionImpl.STARTING_JAVA_PARSER);
        builder.setParentClass(expressionContext.typeContext.getPrimitives().objectParameterizedType);
        builder.setEnclosingType(expressionContext.enclosingType);
        doClassOrInterfaceDeclaration(expressionContext, cid);
        continueInspection(expressionContext, cid.getMembers(), false, false, null);
    }

    // only to be called on primary types
    public void recursivelyAddToTypeStore(TypeMapImpl.Builder typeStore, TypeDeclaration<?> typeDeclaration) {
        recursivelyAddToTypeStore(typeInfo, true, false, typeStore, typeDeclaration);
    }

    private static void recursivelyAddToTypeStore(TypeInfo typeInfo,
                                                  boolean parentIsPrimaryType,
                                                  boolean parentIsDollarType,
                                                  TypeMapImpl.Builder typeStore,
                                                  TypeDeclaration<?> typeDeclaration) {
        typeDeclaration.getMembers().forEach(bodyDeclaration -> {
            bodyDeclaration.ifClassOrInterfaceDeclaration(cid -> {
                DollarResolverResult res = subTypeInfo(typeInfo.fullyQualifiedName, cid.getName().asString(),
                        typeDeclaration, parentIsPrimaryType, parentIsDollarType);
                addToTypeStore(typeStore, res, "type");
                recursivelyAddToTypeStore(res.subType, false, res.isDollarType, typeStore, cid);
            });
            bodyDeclaration.ifEnumDeclaration(ed -> {
                DollarResolverResult res = subTypeInfo(typeInfo.fullyQualifiedName, ed.getName().asString(),
                        typeDeclaration, parentIsPrimaryType, parentIsDollarType);
                addToTypeStore(typeStore, res, "enum");
                recursivelyAddToTypeStore(res.subType, false, res.isDollarType, typeStore, ed);
            });
        });
    }

    private static void addToTypeStore(TypeMapImpl.Builder typeStore, DollarResolverResult res, String what) {
        int inspectionState = res.isDollarType ? TypeInspectionImpl.TRIGGER_BYTECODE_INSPECTION :
                TypeInspectionImpl.STARTING_JAVA_PARSER;
        typeStore.add(res.subType(), inspectionState);
        log(INSPECT, "Added {} to type store: {}", what, res.subType.fullyQualifiedName);
    }

    private List<TypeInfo> continueInspection(
            ExpressionContext expressionContext,
            NodeList<BodyDeclaration<?>> members,
            boolean isInterface,
            boolean haveFunctionalInterface,
            DollarResolver dollarResolver) {
        // first, do sub-types
        ExpressionContext subContext = expressionContext.newVariableContext("body of " + typeInfo.fullyQualifiedName);

        // 2 step approach: first, add these types to the expression context, without inspection
        for (BodyDeclaration<?> bodyDeclaration : members) {
            bodyDeclaration.ifClassOrInterfaceDeclaration(cid -> prepareSubType(expressionContext, dollarResolver, cid.getNameAsString()));
            bodyDeclaration.ifEnumDeclaration(ed -> prepareSubType(expressionContext, dollarResolver, ed.getNameAsString()));
        }

        // then inspect them...
        List<TypeInfo> dollarTypes = new ArrayList<>();
        for (BodyDeclaration<?> bodyDeclaration : members) {
            bodyDeclaration.ifClassOrInterfaceDeclaration(cid -> inspectSubType(dollarResolver, dollarTypes, expressionContext, isInterface,
                    cid.getNameAsString(), cid.asTypeDeclaration()));
            bodyDeclaration.ifEnumDeclaration(ed -> inspectSubType(dollarResolver, dollarTypes, expressionContext, isInterface,
                    ed.getNameAsString(), ed.asTypeDeclaration()));
        }

        // then, do fields

        for (BodyDeclaration<?> bodyDeclaration : members) {
            bodyDeclaration.ifFieldDeclaration(fd -> {
                List<AnnotationExpression> annotations = fd.getAnnotations().stream()
                        .map(ae -> AnnotationExpressionImpl.inspect(expressionContext, ae)).collect(Collectors.toList());
                List<FieldModifier> modifiers = fd.getModifiers().stream()
                        .map(FieldModifier::from)
                        .collect(Collectors.toList());
                for (VariableDeclarator vd : fd.getVariables()) {
                    ParameterizedType pt = ParameterizedType.from(expressionContext.typeContext, vd.getType());

                    String name = vd.getNameAsString();
                    FieldInfo fieldInfo = new FieldInfo(pt, name, typeInfo);
                    FieldInspection inMap = expressionContext.typeContext.getFieldInspection(fieldInfo);
                    FieldInspectionImpl.Builder fieldInspectionBuilder;
                    if (inMap == null) {
                        fieldInspectionBuilder = new FieldInspectionImpl.Builder();
                        expressionContext.typeContext.typeMapBuilder.registerFieldInspection(fieldInfo, fieldInspectionBuilder);
                    } else if (inMap instanceof FieldInspectionImpl.Builder builder) fieldInspectionBuilder = builder;
                    else throw new UnsupportedOperationException();

                    fieldInspectionBuilder.addAnnotations(annotations);
                    if (fullInspection) {
                        fieldInspectionBuilder.addModifiers(modifiers);
                        if (isInterface) {
                            fieldInspectionBuilder
                                    .addModifier(FieldModifier.STATIC)
                                    .addModifier(FieldModifier.FINAL)
                                    .addModifier(FieldModifier.PUBLIC);
                        }
                        if (vd.getInitializer().isPresent()) {
                            fieldInspectionBuilder.setInitializer(vd.getInitializer().get());
                        }
                        builder.addField(fieldInfo);
                    }
                }
            });
        }

        // finally, do constructors and methods

        log(INSPECT, "Variable context after parsing fields of type {}: {}", typeInfo.fullyQualifiedName, subContext.variableContext);

        AtomicInteger countNonStaticNonDefaultIfInterface = new AtomicInteger();
        Map<CompanionMethodName, MethodInspectionImpl.Builder> companionMethodsWaiting = new LinkedHashMap<>();

        for (BodyDeclaration<?> bodyDeclaration : members) {
            bodyDeclaration.ifConstructorDeclaration(cd -> {
                MethodInfo methodInfo = new MethodInfo(typeInfo, List.of());
                MethodInspector methodInspector = new MethodInspector(expressionContext.typeContext.typeMapBuilder, methodInfo,
                        fullInspection);
                methodInspector.inspect(cd, subContext, companionMethodsWaiting, dollarResolver);
                builder.addConstructor(methodInfo);
                companionMethodsWaiting.clear();
            });
            bodyDeclaration.ifMethodDeclaration(md -> {
                // NOTE: it is possible that the return type is unknown at this moment: it can be one of the type
                // parameters that we'll be parsing soon at inspection. That's why we can live with "void" for now
                String methodName = MethodInfo.dropDollarGetClass(md.getName().getIdentifier());
                CompanionMethodName companionMethodName = CompanionMethodName.extract(methodName);
                boolean methodFullInspection = fullInspection || companionMethodName != null;

                MethodInfo methodInfo = new MethodInfo(typeInfo, methodName, List.of(),
                        expressionContext.typeContext.getPrimitives().voidParameterizedType, md.isStatic(), md.isDefault());
                MethodInspector methodInspector = new MethodInspector(expressionContext.typeContext.typeMapBuilder, methodInfo,
                        methodFullInspection);
                methodInspector.inspect(isInterface, md, subContext,
                        companionMethodName != null ? Map.of() : companionMethodsWaiting, dollarResolver);
                if (isInterface && !methodInfo.isStatic && !methodInfo.isDefaultImplementation) {
                    countNonStaticNonDefaultIfInterface.incrementAndGet();
                }
                if (companionMethodName != null) {
                    companionMethodsWaiting.put(companionMethodName, methodInspector.getBuilder()); // will be built with its main method
                } else {
                    builder.addMethod(methodInfo);
                    companionMethodsWaiting.clear();
                }
            });
        }

        if (countNonStaticNonDefaultIfInterface.get() == 1 && !haveFunctionalInterface && fullInspection) {
            boolean haveNonStaticNonDefaultsInSuperType = false;
            for (ParameterizedType superInterface : builder.getInterfacesImplemented()) {
                assert superInterface.typeInfo != null;
                if (superInterface.typeInfo.typeInspection.get().haveNonStaticNonDefaultMethods()) {
                    haveNonStaticNonDefaultsInSuperType = true;
                    break;
                }
            }
            if (!haveNonStaticNonDefaultsInSuperType) {
                builder.addAnnotation(expressionContext.typeContext.getPrimitives().functionalInterfaceAnnotationExpression);
            }
        }
        log(INSPECT, "Setting type inspection of {}", typeInfo.fullyQualifiedName);
        typeInfo.typeInspection.set(builder.build());
        return dollarTypes;
    }

    private record DollarResolverResult(TypeInfo subType, boolean isDollarType) {
    }

    private void prepareSubType(ExpressionContext expressionContext, DollarResolver dollarResolver, String nameAsString) {
        DollarResolverResult res = subType(expressionContext, dollarResolver, nameAsString);
        expressionContext.typeContext.addToContext(res.subType);
        if (res.isDollarType) { // dollar name
            expressionContext.typeContext.addToContext(nameAsString, res.subType, false);
        }
    }

    private void inspectSubType(DollarResolver dollarResolver,
                                List<TypeInfo> dollarTypes,
                                ExpressionContext expressionContext,
                                boolean isInterface,
                                String nameAsString,
                                TypeDeclaration<?> asTypeDeclaration) {
        DollarResolverResult res = subType(expressionContext, dollarResolver, nameAsString);
        TypeInfo subType = res.subType;
        ExpressionContext newExpressionContext = expressionContext.newSubType(subType);
        TypeInfo enclosingType = res.isDollarType ? null : typeInfo;
        boolean typeFullInspection = fullInspection && !res.isDollarType;
        TypeInspector subTypeInspector = new TypeInspector(expressionContext.typeContext.typeMapBuilder, subType, typeFullInspection);
        subTypeInspector.inspect(isInterface, enclosingType, asTypeDeclaration, newExpressionContext, dollarResolver);
        if (res.isDollarType) {
            dollarTypes.add(subType);
        } else {
            builder.addSubType(subType);
        }
    }

    private DollarResolverResult subType(ExpressionContext expressionContext, DollarResolver dollarResolver, String name) {
        TypeInfo subType = dollarResolver == null ? null : dollarResolver.apply(name);
        if (subType != null) {
            return new DollarResolverResult(subType, true);
        }
        TypeInfo fromStore = expressionContext.typeContext.typeMapBuilder.get(typeInfo.fullyQualifiedName + "." + name);
        if (fromStore == null)
            throw new UnsupportedOperationException("I should already know type " + name +
                    " inside " + typeInfo.fullyQualifiedName);
        return new DollarResolverResult(fromStore, false);
    }

    public interface DollarResolver extends Function<String, TypeInfo> {
    }

    /* the following three methods are part of the annotated API system.
    Briefly, if a first-level subtype's name ends with a $, its FQN is composed by the PACKAGE_NAME field in the primary type
    and the subtype name without the $.
     */
    private static DollarResolverResult subTypeInfo(String fullyQualifiedName, String simpleName,
                                                    TypeDeclaration<?> typeDeclaration,
                                                    boolean isPrimaryType,
                                                    boolean parentIsDollarType) {
        if (simpleName.endsWith("$")) {
            if (!isPrimaryType) throw new UnsupportedOperationException();
            String packageName = packageName(typeDeclaration.getFieldByName(PACKAGE_NAME_FIELD).orElse(null));
            if (packageName != null) {
                TypeInfo dollarType = TypeInfo.createFqnOrPackageNameDotSimpleName(packageName, simpleName.substring(0, simpleName.length() - 1));
                return new DollarResolverResult(dollarType, true);
            }
        }
        return new DollarResolverResult(TypeInfo.createFqnOrPackageNameDotSimpleName(fullyQualifiedName, simpleName),
                parentIsDollarType);
    }

    private static String packageName(FieldDeclaration packageNameField) {
        if (packageNameField != null) {
            if (packageNameField.isFinal() && packageNameField.isStatic()) {
                Optional<Expression> initialiser = packageNameField.getVariable(0).getInitializer();
                if (initialiser.isPresent()) {
                    return initialiser.get().asStringLiteralExpr().getValue();
                }
            }
        }
        return null;
    }

    private static TypeNature typeNature(TypeDeclaration<?> typeDeclaration) {
        if (typeDeclaration instanceof ClassOrInterfaceDeclaration cid) {
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
}
