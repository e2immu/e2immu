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

import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithTypeParameters;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.TypeParameter;
import org.e2immu.analyser.inspector.util.EnumMethods;
import org.e2immu.analyser.inspector.util.RecordSynthetics;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.TypeMapImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.*;
import static org.e2immu.analyser.util.Logger.LogTarget.INSPECTOR;
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

DollarTypesAreNormalTypes is used when testing the inspection of annotated API files as plain Java files.
 */

public class TypeInspector {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeInspector.class);
    public static final String PACKAGE_NAME_FIELD = "PACKAGE_NAME";

    private final TypeInfo typeInfo;
    private final TypeInspectionImpl.Builder builder;
    private final boolean fullInspection; // !fullInspection == isDollarType
    private final boolean dollarTypesAreNormalTypes;

    public TypeInspector(TypeMapImpl.Builder typeMapBuilder, TypeInfo typeInfo, boolean fullInspection,
                         boolean dollarTypesAreNormalTypes) {
        this.typeInfo = typeInfo;
        this.dollarTypesAreNormalTypes = dollarTypesAreNormalTypes;

        TypeInspection typeInspection = typeMapBuilder.getTypeInspection(typeInfo);
        if (typeInspection == null || typeInspection.getInspectionState().ge(FINISHED_JAVA_PARSER)) {
            throw new UnsupportedOperationException();
        }
        this.fullInspection = fullInspection;
        builder = (TypeInspectionImpl.Builder) typeInspection;
        builder.setInspectionState(STARTING_JAVA_PARSER);
    }

    public void inspectAnonymousType(ParameterizedType typeImplemented,
                                     ExpressionContext expressionContext,
                                     NodeList<BodyDeclaration<?>> members) {
        assert fullInspection; // no way we could reach this otherwise

        ExpressionContext withSubTypes = expressionContext.newSubType(typeImplemented.typeInfo);
        TypeInspection superInspection = expressionContext.typeContext().getTypeInspection(typeImplemented.typeInfo);
        superInspection.subTypes().forEach(withSubTypes.typeContext()::addToContext);

        if (superInspection.typeNature() == TypeNature.INTERFACE) {
            builder.noParent(withSubTypes.typeContext().getPrimitives());
            builder.addInterfaceImplemented(typeImplemented);
        } else {
            builder.setParentClass(typeImplemented);
        }
        continueInspection(withSubTypes, members, false, null, null);
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
        List<TypeInfo> dollarTypes = inspect(enclosingTypeIsInterface, typeDeclaration, expressionContext, null);
        if (enclosingType == null) {
            dollarTypes.add(0, typeInfo);
        }
        return dollarTypes;
    }

    /*
    returns the primary type in normal cases, and primary types in case of $ types
     */
    private List<TypeInfo> inspect(boolean enclosingTypeIsInterface,
                                   TypeDeclaration<?> typeDeclaration,
                                   ExpressionContext expressionContext,
                                   DollarResolver dollarResolverInput) {
        LOGGER.info("Inspecting type {}", typeInfo.fullyQualifiedName);
        assert typeDeclaration != null;
        builder.setPositionalIdentifier(Identifier.from(typeDeclaration.getBegin().orElse(null),
                typeDeclaration.getEnd().orElse(null)));

        TypeContext typeContext = expressionContext.typeContext();

        DollarResolver dollarResolver = getDollarResolver(typeDeclaration, dollarResolverInput, typeContext);
        if (fullInspection) {
            builder.noParent(typeContext.getPrimitives());
            if (enclosingTypeIsInterface) {
                builder.addTypeModifier(TypeModifier.PUBLIC);
                builder.addTypeModifier(TypeModifier.STATIC);
            }
        }
        typeContext.addToContext(typeInfo);

        for (AnnotationExpr annotationExpr : typeDeclaration.getAnnotations()) {
            AnnotationExpression ae = AnnotationInspector.inspect(expressionContext, annotationExpr);
            builder.addAnnotation(ae);
        }
        List<RecordField> recordFields = null;
        if (fullInspection) {
            try {
                if (typeDeclaration instanceof RecordDeclaration rd) {
                    recordFields = doRecordDeclaration(expressionContext, rd);
                } else if (typeDeclaration instanceof EnumDeclaration ed) {
                    doEnumDeclaration(expressionContext, ed);
                } else if (typeDeclaration instanceof AnnotationDeclaration ad) {
                    doAnnotationDeclaration(expressionContext, ad);
                } else if (typeDeclaration instanceof ClassOrInterfaceDeclaration cid) {
                    doClassOrInterfaceDeclaration(expressionContext, cid);
                }

                for (Modifier modifier : typeDeclaration.getModifiers()) {
                    builder.addTypeModifier(TypeModifier.from(modifier));
                }
            } catch (RuntimeException rte) {
                LOGGER.error("Caught runtime exception while parsing type declaration at line " + typeDeclaration.getBegin());
                throw rte;
            }
        } else if (typeDeclaration instanceof ClassOrInterfaceDeclaration cid) {
            // even if we're inspecting dollar types (no full inspection), we need to keep track
            // of the type parameters
            int tpIndex = 0;
            for (com.github.javaparser.ast.type.TypeParameter typeParameter : cid.getTypeParameters()) {
                TypeParameterImpl tp = new TypeParameterImpl(typeInfo, typeParameter.getNameAsString(), tpIndex);
                typeContext.addToContext(tp);
                tp.inspect(typeContext, typeParameter);

                boolean annotatedWithIndependent = isAnnotatedWithIndependent(typeParameter, expressionContext);
                tp.setAnnotatedWithIndependent(annotatedWithIndependent);
                TypeParameterImpl original = (TypeParameterImpl) builder.typeParameters().get(tpIndex);
                if (original.isAnnotatedWithIndependent() == null)
                    original.setAnnotatedWithIndependent(annotatedWithIndependent);
                tpIndex++;
            }
        }
        return continueInspection(expressionContext, typeDeclaration.getMembers(),
                builder.typeNature() == TypeNature.INTERFACE, recordFields, dollarResolver);
    }

    private DollarResolver getDollarResolver(TypeDeclaration<?> typeDeclaration, DollarResolver dollarResolverInput, TypeContext typeContext) {
        DollarResolver dollarResolver;
        if (typeInfo.isPrimaryType()) {
            if (dollarTypesAreNormalTypes) {
                dollarResolver = name -> null;
            } else {
                FieldDeclaration packageNameField = typeDeclaration.getFieldByName(PACKAGE_NAME_FIELD).orElse(null);
                String dollarPackageName = TypeInspectionImpl.packageName(packageNameField);
                dollarResolver = name -> {
                    if (name.endsWith("$") && dollarPackageName != null) {
                        return typeContext.typeMapBuilder
                                .getOrCreate(dollarPackageName, name.substring(0, name.length() - 1), TRIGGER_BYTECODE_INSPECTION);
                    }
                    return null;
                };
            }
        } else {
            dollarResolver = dollarResolverInput;
        }
        return dollarResolver;
    }

    private void doAnnotationDeclaration(ExpressionContext expressionContext, AnnotationDeclaration annotationDeclaration) {
        builder.setTypeNature(TypeNature.ANNOTATION);
        ExpressionContext subContext = expressionContext.newVariableContext("annotation body of " + typeInfo.fullyQualifiedName);

        for (BodyDeclaration<?> bd : annotationDeclaration.getMembers()) {
            if (bd.isAnnotationMemberDeclaration()) {
                AnnotationMemberDeclaration amd = bd.asAnnotationMemberDeclaration();
                log(INSPECTOR, "Have member {} in {}", amd.getNameAsString(), typeInfo.fullyQualifiedName);
                TypeMapImpl.Builder typeMapBuilder = expressionContext.typeContext().typeMapBuilder;
                MethodInspector methodInspector = new MethodInspector(typeMapBuilder, typeInfo, fullInspection);
                methodInspector.inspect(amd, subContext);
                builder.addMethod(methodInspector.getBuilder().getMethodInfo());
            }
        }
    }

    public record RecordField(FieldInfo fieldInfo, boolean varargs) {
    }

    private List<RecordField> doRecordDeclaration(ExpressionContext expressionContext,
                                                  RecordDeclaration recordDeclaration) {
        builder.setTypeNature(TypeNature.RECORD);

        doTypeParameters(expressionContext, recordDeclaration);
        doImplementedTypes(expressionContext, recordDeclaration.getImplementedTypes());

        return recordDeclaration.getParameters().stream().map(parameter -> {
            boolean varargs = parameter.isVarArgs();
            ParameterizedType type = ParameterizedTypeFactory.from(expressionContext.typeContext(), parameter.getType(), varargs, null);
            FieldInfo fieldInfo = new FieldInfo(Identifier.from(parameter), type, parameter.getNameAsString(), typeInfo);

            FieldInspectionImpl.Builder fieldBuilder = new FieldInspectionImpl.Builder();
            fieldBuilder.setSynthetic(true);
            fieldBuilder.addModifier(FieldModifier.FINAL);
            fieldBuilder.addModifier(FieldModifier.PRIVATE);
            expressionContext.typeContext().typeMapBuilder.registerFieldInspection(fieldInfo, fieldBuilder);
            builder.addField(fieldInfo);

            return new RecordField(fieldInfo, varargs);
        }).toList();
    }

    private void doEnumDeclaration(ExpressionContext expressionContext, EnumDeclaration enumDeclaration) {
        builder.setTypeNature(TypeNature.ENUM);

        TypeInfo enumTypeInfo = expressionContext.typeContext().getFullyQualified(Enum.class);

        ParameterizedType parent = new ParameterizedType(enumTypeInfo, List.of(new ParameterizedType(builder.typeInfo(), List.of())));
        builder.setParentClass(parent);

        doImplementedTypes(expressionContext, enumDeclaration.getImplementedTypes());

        List<FieldInfo> enumFields = new ArrayList<>();

        enumDeclaration.getEntries().forEach(enumConstantDeclaration -> {
            FieldInfo fieldInfo = new FieldInfo(Identifier.from(enumConstantDeclaration),
                    typeInfo.asSimpleParameterizedType(),
                    enumConstantDeclaration.getNameAsString(), typeInfo);
            FieldInspectionImpl.Builder fieldBuilder = new FieldInspectionImpl.Builder();
            fieldBuilder.setSynthetic(true);
            fieldBuilder.addModifier(FieldModifier.FINAL);
            fieldBuilder.addModifier(FieldModifier.PUBLIC);
            fieldBuilder.addModifier(FieldModifier.STATIC);
            ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr();
            objectCreationExpr.setArguments(enumConstantDeclaration.getArguments());
            objectCreationExpr.setType(typeInfo.simpleName);
            objectCreationExpr.setRange(enumConstantDeclaration.getRange().orElseThrow());
            fieldBuilder.setInitialiserExpression(objectCreationExpr); // = new EnumType(...)
            expressionContext.typeContext().typeMapBuilder.registerFieldInspection(fieldInfo, fieldBuilder);
            builder.addField(fieldInfo);
            enumFields.add(fieldInfo);
        });

        EnumMethods.create(expressionContext, typeInfo, builder, enumFields);
    }

    private void doTypeParameters(ExpressionContext expressionContext, NodeWithTypeParameters<?> node) {
        int tpIndex = 0;
        for (com.github.javaparser.ast.type.TypeParameter typeParameter : node.getTypeParameters()) {
            TypeParameterImpl tp = new TypeParameterImpl(typeInfo, typeParameter.getNameAsString(), tpIndex++);
            expressionContext.typeContext().addToContext(tp);
            tp.inspect(expressionContext.typeContext(), typeParameter);
            builder.addTypeParameter(tp);
            boolean annotatedWithIndependent = isAnnotatedWithIndependent(typeParameter, expressionContext);
            if (tp.isAnnotatedWithIndependent() == null) tp.setAnnotatedWithIndependent(annotatedWithIndependent);
        }
    }

    private boolean isAnnotatedWithIndependent(TypeParameter typeParameter, ExpressionContext expressionContext) {
        return typeParameter.getAnnotations().stream()
                .map(ae -> AnnotationInspector.inspect(expressionContext, ae))
                .anyMatch(ae -> ae.equals(
                        expressionContext.typeContext().typeMapBuilder.getE2ImmuAnnotationExpressions().independent));
    }

    private void doImplementedTypes(ExpressionContext expressionContext,
                                    NodeList<ClassOrInterfaceType> implementedTypes) {
        for (ClassOrInterfaceType extended : implementedTypes) {
            ParameterizedType parameterizedType = ParameterizedTypeFactory.from(expressionContext.typeContext(), extended);
            if (fullInspection) ensureLoaded(expressionContext, parameterizedType);
            builder.addInterfaceImplemented(parameterizedType);
        }
    }

    private void doClassOrInterfaceDeclaration(ExpressionContext expressionContext,
                                               ClassOrInterfaceDeclaration cid) {
        doTypeParameters(expressionContext, cid);
        if (cid.isInterface()) {
            builder.setTypeNature(TypeNature.INTERFACE);
            doImplementedTypes(expressionContext, cid.getExtendedTypes());
        } else {
            builder.setTypeNature(TypeNature.CLASS);
            if (!cid.getExtendedTypes().isEmpty()) {
                ParameterizedType parameterizedType = ParameterizedTypeFactory.from(expressionContext.typeContext(), cid.getExtendedTypes(0));
                // why this check? hasBeenDefined == true signifies Java parsing; == false is annotated APIs.
                // the annotated APIs are backed by .class files, which can be inspected with byte code; there, we only have
                // fully qualified names. In Java, we must add type names of parent's subtypes etc.
                if (fullInspection) ensureLoaded(expressionContext, parameterizedType);
                builder.setParentClass(parameterizedType);
            }
            doImplementedTypes(expressionContext, cid.getImplementedTypes());
        }
    }

    /**
     * calling "get" on the typeInspection of the parameterizedType will trigger recursive parsing.
     * But we should not do that when we're inside the same compilation unit: the primary type and all its subtypes.
     */
    private void ensureLoaded(ExpressionContext expressionContext, ParameterizedType parameterizedType) {
        assert parameterizedType.typeInfo != null;
        boolean insideCompilationUnit = parameterizedType.typeInfo.fullyQualifiedName.startsWith(expressionContext.primaryType().fullyQualifiedName);
        if (!insideCompilationUnit) {
            InspectionProvider inspectionProvider = expressionContext.typeContext().typeMapBuilder;
            // getting the type inspection should trigger either byte-code or java inspection
            TypeInspection typeInspection = inspectionProvider.getTypeInspection(parameterizedType.typeInfo);
            if (typeInspection == null) {
                throw new UnsupportedOperationException("The type " + parameterizedType.typeInfo.fullyQualifiedName + " should already have a type inspection");
            }
            // now that we're sure it has been inspected, we add all its top-level subtypes to the type context
            for (TypeInfo subType : typeInspection.subTypes()) {
                expressionContext.typeContext().addToContext(subType);
            }
            // parentClass can be null in rare occasions where there is a cyclic dependency between types inside two primary types
            // see Immutables-generated code
            ParameterizedType parentClass = typeInspection.parentClass();
            if (parentClass != null && !parentClass.isJavaLangObject()) {
                ensureLoaded(expressionContext, parentClass);
            }
            typeInspection.interfacesImplemented().forEach(i -> ensureLoaded(expressionContext, i));
        }
    }

    public void inspectLocalClassDeclaration(ExpressionContext expressionContext,
                                             ClassOrInterfaceDeclaration cid) {
        builder.noParent(expressionContext.typeContext().getPrimitives());
        doClassOrInterfaceDeclaration(expressionContext, cid);
        builder.addTypeModifier(TypeModifier.PRIVATE);
        continueInspection(expressionContext, cid.getMembers(), false, null, null);
    }

    // only to be called on primary types
    public void recursivelyAddToTypeStore(TypeMapImpl.Builder typeStore, TypeDeclaration<?> typeDeclaration,
                                          boolean dollarTypesAreNormalTypes) {
        assert typeInfo.isPrimaryType() : "Only to be called on primary types";
        builder.recursivelyAddToTypeStore(true, false, typeStore, typeDeclaration,
                dollarTypesAreNormalTypes);
    }

    private List<TypeInfo> continueInspection(
            ExpressionContext expressionContext,
            NodeList<BodyDeclaration<?>> members,
            boolean isInterface,
            List<RecordField> recordFields,
            DollarResolver dollarResolver) {
        TypeContext typeContext = expressionContext.typeContext();

        // first, do sub-types
        ExpressionContext subContext = expressionContext.newVariableContext("body of " + typeInfo.fullyQualifiedName);

        int countCompactConstructors = 0;
        int countNormalConstructors = 0;

        // 2-step approach: first, add these types to the expression context, without inspection
        // while we loop, we do some counting to avoid looping too often

        List<TypeDeclaration<?>> typeDeclarations = new LinkedList<>();
        List<FieldDeclaration> fieldDeclarations = new LinkedList<>();

        for (BodyDeclaration<?> bodyDeclaration : members) {
            if (bodyDeclaration instanceof TypeDeclaration cid) {
                prepareSubType(expressionContext, dollarResolver, cid.getNameAsString());
                typeDeclarations.add(cid);
            } else if (bodyDeclaration instanceof CompactConstructorDeclaration) ++countCompactConstructors;
            else if (bodyDeclaration instanceof ConstructorDeclaration) ++countNormalConstructors;
            else if (bodyDeclaration instanceof FieldDeclaration fd) fieldDeclarations.add(fd);
        }

        // then inspect them...
        List<TypeInfo> dollarTypes = new ArrayList<>();
        for (TypeDeclaration<?> typeDeclaration : typeDeclarations) {
            inspectSubType(dollarResolver, dollarTypes, expressionContext, isInterface,
                    typeDeclaration.getNameAsString(), typeDeclaration);
        }

        // add an empty constructor if no constructors are available, for ENUM and CLASS
        if (countNormalConstructors == 0 && builder.hasEmptyConstructorIfNoConstructorsPresent() && fullInspection) {
            boolean privateEmptyConstructor = builder.typeNature() == TypeNature.ENUM;
            builder.addConstructor(createEmptyConstructor(typeContext, privateEmptyConstructor));
        }

        // then, do normal constructors and methods
        Map<CompanionMethodName, MethodInspectionImpl.Builder> companionMethodsWaiting = new LinkedHashMap<>();
        AtomicInteger countStaticBlocks = new AtomicInteger();

        for (BodyDeclaration<?> bodyDeclaration : members) {
            if (bodyDeclaration instanceof InitializerDeclaration id) {
                initializerDeclaration(expressionContext, countStaticBlocks, id);
            } else if (bodyDeclaration instanceof CompactConstructorDeclaration ccd) {
                compactConstructorDeclaration(expressionContext, recordFields, subContext, companionMethodsWaiting, ccd);
            } else if (bodyDeclaration instanceof ConstructorDeclaration cd) {
                constructorDeclaration(expressionContext, dollarResolver, subContext, companionMethodsWaiting, cd);
            } else if (bodyDeclaration instanceof MethodDeclaration md) {
                methodDeclaration(expressionContext, isInterface, dollarResolver, subContext, companionMethodsWaiting, md);
            }
        }

        // add @FunctionalInterface interface if needed
        if (fullInspection) {
            ensureFunctionalInterfaceAnnotation(typeContext, builder);
        }

        // then, do fields (relies on @FI to be present)
        for (FieldDeclaration fieldDeclaration : fieldDeclarations) {
            fieldDeclaration(expressionContext, isInterface, typeContext, fieldDeclaration);
        }

         /*
        Ensure a constructor when the type is a record and there are no compact constructors.
        (those without arguments, as in 'public record SomeRecord(...) { public Record { this.field = } ... }' )
        and also no default constructor override.
        The latter condition is verified in the builder.ensureConstructor() method.
         */
        if (TypeNature.RECORD == builder.typeNature()) {
            if (countCompactConstructors == 0) {
                ensureCompactConstructor(recordFields, typeContext, subContext);
            }
        }

        // finally, add synthetic methods if needed
        if (recordFields != null) {
            assert TypeNature.RECORD == builder.typeNature();
            RecordSynthetics.ensureAccessors(expressionContext, typeInfo, builder, recordFields);
        }

        log(INSPECTOR, "Setting type inspection of {}", typeInfo.fullyQualifiedName);
        typeInfo.typeInspection.set(builder.build());
        return dollarTypes;
    }

    private void initializerDeclaration(ExpressionContext expressionContext,
                                        AtomicInteger countStaticBlocks,
                                        InitializerDeclaration id) {
        if (fullInspection) {
            MethodInspector methodInspector = new MethodInspector(expressionContext.typeContext().typeMapBuilder,
                    typeInfo, true);
            methodInspector.inspect(id, expressionContext, countStaticBlocks.getAndIncrement());
            builder.ensureMethod(methodInspector.getBuilder().getMethodInfo());
        }
    }

    private void compactConstructorDeclaration(ExpressionContext expressionContext,
                                               List<RecordField> recordFields,
                                               ExpressionContext subContext,
                                               Map<CompanionMethodName, MethodInspectionImpl.Builder> companionMethodsWaiting,
                                               CompactConstructorDeclaration ccd) {
        MethodInspector methodInspector = new MethodInspector(expressionContext.typeContext().typeMapBuilder, typeInfo,
                fullInspection);
        assert recordFields != null;
        methodInspector.inspect(ccd, subContext, companionMethodsWaiting, recordFields);
        builder.ensureConstructor(methodInspector.getBuilder().getMethodInfo());
        companionMethodsWaiting.clear();
    }

    private void constructorDeclaration(ExpressionContext expressionContext,
                                        DollarResolver dollarResolver,
                                        ExpressionContext subContext,
                                        Map<CompanionMethodName, MethodInspectionImpl.Builder> companionMethodsWaiting,
                                        ConstructorDeclaration cd) {
        MethodInspector methodInspector = new MethodInspector(expressionContext.typeContext().typeMapBuilder, typeInfo,
                fullInspection);
        boolean isEnumConstructorMustBePrivate = builder.typeNature() == TypeNature.ENUM;
        methodInspector.inspect(cd, subContext, companionMethodsWaiting, dollarResolver, isEnumConstructorMustBePrivate);
        builder.ensureConstructor(methodInspector.getBuilder().getMethodInfo());
        companionMethodsWaiting.clear();
    }

    private void fieldDeclaration(ExpressionContext expressionContext,
                                  boolean isInterface,
                                  TypeContext typeContext,
                                  FieldDeclaration fd) {
        List<AnnotationExpression> annotations = fd.getAnnotations().stream()
                .map(ae -> AnnotationInspector.inspect(expressionContext, ae)).collect(Collectors.toList());
        List<FieldModifier> modifiers = fd.getModifiers().stream()
                .map(FieldModifier::from)
                .collect(Collectors.toList());
        for (VariableDeclarator vd : fd.getVariables()) {
            singleFieldDeclaration(expressionContext, isInterface, typeContext, fd, annotations, modifiers, vd);
        }
    }

    private void singleFieldDeclaration(ExpressionContext expressionContext,
                                        boolean isInterface,
                                        TypeContext typeContext,
                                        FieldDeclaration fd,
                                        List<AnnotationExpression> annotations,
                                        List<FieldModifier> modifiers,
                                        VariableDeclarator vd) {
        ParameterizedType pt = ParameterizedTypeFactory.from(typeContext, vd.getType());

        String name = vd.getNameAsString();
        FieldInfo fieldInfo = new FieldInfo(Identifier.from(fd), pt, name, typeInfo);

        FieldInspection inMap = typeContext.getFieldInspection(fieldInfo);
        FieldInspectionImpl.Builder fieldInspectionBuilder;
        if (inMap == null) {
            fieldInspectionBuilder = new FieldInspectionImpl.Builder();
            typeContext.typeMapBuilder.registerFieldInspection(fieldInfo, fieldInspectionBuilder);
        } else if (inMap instanceof FieldInspectionImpl.Builder builder) fieldInspectionBuilder = builder;
        else throw new UnsupportedOperationException();

        expressionContext.variableContext().add(new FieldReference(typeContext, fieldInfo));

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
                fieldInspectionBuilder.setInitialiserExpression(vd.getInitializer().get());
            }
            builder.addField(fieldInfo);
        }
    }

    private void methodDeclaration(ExpressionContext expressionContext,
                                   boolean isInterface,
                                   DollarResolver dollarResolver,
                                   ExpressionContext subContext,
                                   Map<CompanionMethodName, MethodInspectionImpl.Builder> companionMethodsWaiting,
                                   MethodDeclaration md) {
        // NOTE: it is possible that the return type is unknown at this moment: it can be one of the type
        // parameters that we'll be parsing soon at inspection. That's why we can live with "void" for now
        String methodName = MethodInfo.dropDollarGetClass(md.getName().getIdentifier());
        CompanionMethodName companionMethodName = CompanionMethodName.extract(methodName);
        boolean methodFullInspection = fullInspection || companionMethodName != null;

        MethodInspector methodInspector = new MethodInspector(expressionContext.typeContext().typeMapBuilder, typeInfo,
                methodFullInspection);
        methodInspector.inspect(isInterface, methodName, md, subContext,
                companionMethodName != null ? Map.of() : companionMethodsWaiting, dollarResolver);
        MethodInspection methodInspection = methodInspector.getBuilder();
        MethodInfo methodInfo = methodInspection.getMethodInfo();
        if (companionMethodName != null) {
            companionMethodsWaiting.put(companionMethodName, methodInspector.getBuilder()); // will be built with its main method
        } else {
            builder.ensureMethod(methodInfo);
            companionMethodsWaiting.clear();
        }
    }

    private void ensureCompactConstructor(List<RecordField> recordFields,
                                          TypeContext typeContext,
                                          ExpressionContext subContext) {
        assert recordFields != null;
        MethodInspector methodInspector = new MethodInspector(typeContext.typeMapBuilder, typeInfo,
                fullInspection);
        boolean created = methodInspector.inspect(null, subContext, Map.of(), recordFields);
        if (created) {
            builder.ensureConstructor(methodInspector.getBuilder().getMethodInfo());
        }
    }

    private void ensureFunctionalInterfaceAnnotation(TypeContext typeContext,
                                                     TypeInspectionImpl.Builder builder) {
        if (builder.typeNature() == TypeNature.INTERFACE && builder.computeIsFunctionalInterface(typeContext)) {
            builder.addAnnotation(typeContext.getPrimitives().functionalInterfaceAnnotationExpression());
            builder.setFunctionalInterface(true);
        } else {
            builder.setFunctionalInterface(false);
        }
    }

    private MethodInfo createEmptyConstructor(TypeContext typeContext, boolean makePrivate) {
        MethodInspectionImpl.Builder builder = new MethodInspectionImpl.Builder(typeInfo);
        builder.setInspectedBlock(Block.emptyBlock(Identifier.generate()))
                .setSynthetic(true)
                .addModifier(makePrivate ? MethodModifier.PRIVATE : MethodModifier.PUBLIC)
                .readyToComputeFQN(typeContext);
        typeContext.typeMapBuilder.registerMethodInspection(builder);
        return builder.getMethodInfo();
    }

    record DollarResolverResult(TypeInfo subType, boolean isDollarType) {
    }

    private void prepareSubType(ExpressionContext expressionContext, DollarResolver dollarResolver, String
            nameAsString) {
        DollarResolverResult res = subType(expressionContext.typeContext().typeMapBuilder, dollarResolver, nameAsString);
        expressionContext.typeContext().addToContext(res.subType);
        if (res.isDollarType) { // dollar name
            expressionContext.typeContext().addToContext(nameAsString, res.subType, false);
        }
    }

    private void inspectSubType(DollarResolver dollarResolver,
                                List<TypeInfo> dollarTypes,
                                ExpressionContext expressionContext,
                                boolean isInterface,
                                String nameAsString,
                                TypeDeclaration<?> asTypeDeclaration) {
        TypeMapImpl.Builder typeMapBuilder = expressionContext.typeContext().typeMapBuilder;
        DollarResolverResult res = subType(typeMapBuilder, dollarResolver, nameAsString);
        TypeInfo subType = res.subType;
        ExpressionContext newExpressionContext = expressionContext.newSubType(subType);
        boolean typeFullInspection = fullInspection && !res.isDollarType;
        TypeInspector subTypeInspector = new TypeInspector(typeMapBuilder, subType, typeFullInspection, dollarTypesAreNormalTypes);
        subTypeInspector.inspect(isInterface, asTypeDeclaration, newExpressionContext, dollarResolver);
        if (res.isDollarType) {
            dollarTypes.add(subType);
        } else {
            builder.ensureSubType(subType);
        }
    }

    private DollarResolverResult subType(TypeMapImpl.Builder typeMapBuilder, DollarResolver dollarResolver, String
            name) {
        TypeInfo subType = dollarResolver == null ? null : dollarResolver.apply(name);
        if (subType != null) {
            return new DollarResolverResult(subType, true);
        }
        TypeInfo fromStore = typeMapBuilder.get(typeInfo.fullyQualifiedName + "." + name);
        if (fromStore == null)
            throw new UnsupportedOperationException("I should already know type " + name +
                    " inside " + typeInfo.fullyQualifiedName);
        return new DollarResolverResult(fromStore, false);
    }

    public interface DollarResolver extends Function<String, TypeInfo> {
    }

    public TypeInfo getTypeInfo() {
        return typeInfo;
    }
}
