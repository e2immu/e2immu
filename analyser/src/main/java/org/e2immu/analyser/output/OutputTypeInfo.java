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

package org.e2immu.analyser.output;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ConstructorCall;
import org.e2immu.analyser.model.impl.QualificationImpl;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OutputTypeInfo {
    public static OutputBuilder output(TypeInfo typeInfo, Qualification qualification, boolean doTypeDeclaration) {
        String typeNature;
        Set<String> imports;
        Qualification insideType;
        if (typeInfo.isPrimaryType() && typeInfo.hasBeenInspected()) {
            ResultOfImportComputation res = imports(typeInfo.packageName(), typeInfo);
            imports = res.imports;
            insideType = res.qualification;
        } else {
            imports = Set.of();
            insideType = typeInfo.hasBeenInspected() && qualification instanceof QualificationImpl ? new QualificationImpl(qualification) : qualification;
        }
        assert insideType != null;

        List<TypeModifier> typeModifiers;
        List<FieldInfo> fields;
        List<MethodInfo> constructors;
        List<MethodInfo> methods;
        List<TypeInfo> subTypes;
        List<ParameterizedType> interfaces;
        List<TypeParameter> typeParameters;
        ParameterizedType parentClass;
        boolean isInterface;
        boolean isRecord;
        Comment comment;

        if (typeInfo.hasBeenInspected()) {
            TypeInspection typeInspection = typeInfo.typeInspection.get();
            typeNature = typeInspection.typeNature().toJava();
            isInterface = typeInspection.isInterface();
            isRecord = typeInspection.typeNature() == TypeNature.RECORD;
            typeModifiers = minimalModifiers(typeInspection);
            fields = typeInspection.fields();
            constructors = typeInspection.constructors();
            methods = typeInspection.methods();
            subTypes = typeInspection.subTypes();
            typeParameters = typeInspection.typeParameters();
            parentClass = typeInfo.parentIsNotJavaLangObject() ? typeInspection.parentClass() : null;
            interfaces = typeInspection.interfacesImplemented();
            comment = typeInspection.getComment();

            // add the methods that we can call without having to qualify (method() instead of super.method())
            if (insideType instanceof QualificationImpl qi) {
                addMethodsToQualification(typeInfo, qi);
                addThisToQualification(typeInfo, qi);
            }
        } else {
            typeNature = "class"; // we really have no idea what it is
            typeModifiers = List.of(TypeModifier.ABSTRACT);
            fields = List.of();
            constructors = List.of();
            methods = List.of();
            subTypes = List.of();
            typeParameters = List.of();
            interfaces = List.of();
            parentClass = null;
            isInterface = false;
            isRecord = false;
            comment = null;
        }

        // PACKAGE AND IMPORTS

        OutputBuilder packageAndImports = new OutputBuilder();
        if (typeInfo.isPrimaryType()) {
            String packageName = typeInfo.packageNameOrEnclosingType.getLeftOrElse("");
            if (!packageName.isEmpty()) {
                packageAndImports.add(new Text("package")).add(Space.ONE).add(new Text(packageName)).add(Symbol.SEMICOLON)
                        .add(Space.NEWLINE);
            }
            if (!imports.isEmpty()) {
                imports.stream().sorted().forEach(i ->
                        packageAndImports.add(new Text("import")).add(Space.ONE).add(new Text(i)).add(Symbol.SEMICOLON)
                                .add(Space.NEWLINE));
            }
        }

        OutputBuilder afterAnnotations = new OutputBuilder();
        if (doTypeDeclaration) {
            // the class name
            afterAnnotations
                    .add(typeModifiers.stream().map(mod -> new OutputBuilder().add(new Text(mod.toJava())))
                            .collect(OutputBuilder.joining(Space.ONE)))
                    .add(Space.ONE).add(new Text(typeNature))
                    .add(Space.ONE).add(new Text(typeInfo.simpleName));

            if (!typeParameters.isEmpty()) {
                afterAnnotations.add(Symbol.LEFT_ANGLE_BRACKET);
                afterAnnotations.add(typeParameters.stream().map(tp ->
                                tp.output(InspectionProvider.DEFAULT, insideType, new HashSet<>()))
                        .collect(OutputBuilder.joining(Symbol.COMMA)));
                afterAnnotations.add(Symbol.RIGHT_ANGLE_BRACKET);
            }
            if (isRecord) {
                afterAnnotations.add(outputNonStaticFieldsAsParameters(insideType, fields));
            }
            if (parentClass != null) {
                afterAnnotations.add(Space.ONE).add(new Text("extends")).add(Space.ONE).add(parentClass.output(insideType));
            }
            if (!interfaces.isEmpty()) {
                afterAnnotations.add(Space.ONE).add(new Text(isInterface ? "extends" : "implements")).add(Space.ONE);
                afterAnnotations.add(interfaces.stream().map(pi -> pi.output(insideType)).collect(OutputBuilder.joining(Symbol.COMMA)));
            }
        }

        OutputBuilder main = Stream.concat(Stream.concat(Stream.concat(Stream.concat(
                                                enumConstantStream(typeInfo, insideType),
                                                fields.stream()
                                                        .filter(f -> !f.fieldInspection.get().isSynthetic())
                                                        .map(f -> f.output(insideType, false))),
                                        subTypes.stream()
                                                .filter(st -> !st.typeInspection.get().isSynthetic())
                                                .map(ti -> ti.output(insideType, true))),
                                constructors.stream()
                                        .filter(c -> !c.methodInspection.get().isSynthetic())
                                        .map(c -> c.output(insideType))),
                        methods.stream()
                                .filter(m -> !m.methodInspection.get().isSynthetic())
                                .map(m -> m.output(insideType)))
                .collect(OutputBuilder.joining(Space.NONE, Symbol.LEFT_BRACE, Symbol.RIGHT_BRACE,
                        Guide.generatorForBlock()));
        afterAnnotations.add(main);

        // annotations and the rest of the type are at the same level
        Stream<OutputBuilder> annotationStream = doTypeDeclaration ? typeInfo.buildAnnotationOutput(insideType) : Stream.of();
        if(comment != null) packageAndImports.add(comment.output(qualification));
        return packageAndImports.add(Stream.concat(annotationStream, Stream.of(afterAnnotations))
                .collect(OutputBuilder.joining(Space.ONE_REQUIRED_EASY_SPLIT,
                        Guide.generatorForAnnotationList())));
    }

    private static List<TypeModifier> minimalModifiers(TypeInspection typeInspection) {
        Set<TypeModifier> modifiers = typeInspection.modifiers();
        List<TypeModifier> list = new ArrayList<>();

        // access
        Inspection.Access access = typeInspection.getAccess();
        Inspection.Access enclosedAccess = typeInspection.typeInfo().packageNameOrEnclosingType.isLeft()
                ? Inspection.Access.PUBLIC : typeInspection.typeInfo().packageNameOrEnclosingType.getRight().typeInspection.get().getAccess();
        if (enclosedAccess != Inspection.Access.PRIVATE && access != Inspection.Access.PACKAGE) {
            list.add(typeModifier(access));
        } // else there really is no point anymore to show any access modifier, let's keep it brief

        // 'abstract', 'static'
        if (typeInspection.typeNature() == TypeNature.CLASS) {
            if (modifiers.contains(TypeModifier.ABSTRACT)) {
                list.add(TypeModifier.ABSTRACT);
            }
            if (modifiers.contains(TypeModifier.STATIC)) {
                list.add(TypeModifier.STATIC);
            }
            if (modifiers.contains(TypeModifier.FINAL)) {
                list.add(TypeModifier.FINAL);
            }
            if (modifiers.contains(TypeModifier.SEALED)) {
                list.add(TypeModifier.SEALED);
            }
            if (modifiers.contains(TypeModifier.NON_SEALED)) {
                list.add(TypeModifier.NON_SEALED);
            }
        } // else: records, interfaces, annotations, primitives are always static, never abstract

        return list;
    }

    private static TypeModifier typeModifier(Inspection.Access access) {
        return switch (access) {
            case PUBLIC -> TypeModifier.PUBLIC;
            case PROTECTED -> TypeModifier.PROTECTED;
            case PRIVATE -> TypeModifier.PRIVATE;
            default -> throw new UnsupportedOperationException();
        };
    }

    private static OutputBuilder outputNonStaticFieldsAsParameters(Qualification qualification, List<FieldInfo> fields) {
        return fields.stream()
                .filter(fieldInfo -> !fieldInfo.fieldInspection.get().isStatic())
                .map(fieldInfo -> fieldInfo.output(qualification, true))
                .collect(OutputBuilder.joining(Symbol.COMMA, Symbol.LEFT_PARENTHESIS, Symbol.RIGHT_PARENTHESIS,
                        Guide.generatorForParameterDeclaration()));
    }

    private static void addThisToQualification(TypeInfo typeInfo, QualificationImpl insideType) {
        insideType.addThis(new This(InspectionProvider.DEFAULT, typeInfo));
        ParameterizedType parentClass = typeInfo.typeInspection.get().parentClass();
        if (parentClass != null && !parentClass.isJavaLangObject()) {
            insideType.addThis(new This(InspectionProvider.DEFAULT, parentClass.typeInfo,
                    null, true));
        }
    }

    private static void addMethodsToQualification(TypeInfo typeInfo, QualificationImpl qImpl) {
        TypeInspection ti = typeInfo.typeInspection.get("Inspection of type " + typeInfo.fullyQualifiedName);
        ti.methods().forEach(qImpl::addMethodUnlessOverride);
        if (!typeInfo.isJavaLangObject()) {
            addMethodsToQualification(ti.parentClass().typeInfo, qImpl);
        }
        for (ParameterizedType interfaceType : ti.interfacesImplemented()) {
            addMethodsToQualification(interfaceType.typeInfo, qImpl);
        }
    }

    private static Stream<OutputBuilder> enumConstantStream(TypeInfo typeInfo, Qualification qualification) {
        if (typeInfo.typeInspection.get().typeNature() == TypeNature.ENUM) {
            Guide.GuideGenerator gg = Guide.generatorForEnumDefinitions();
            OutputBuilder outputBuilder = new OutputBuilder().add(gg.start());
            boolean first = true;
            for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields()) {
                if (fieldInfo.fieldInspection.get().isSynthetic()) {
                    if (first) {
                        first = false;
                    } else {
                        outputBuilder.add(Symbol.COMMA).add(gg.mid());
                    }
                    outputBuilder.add(new Text(fieldInfo.name));
                    FieldInspection.FieldInitialiser fieldInitialiser = fieldInfo.fieldInspection.get().getFieldInitialiser();
                    Expression initialiser = fieldInitialiser == null ? null : fieldInitialiser.initialiser();
                    ConstructorCall constructorCall;
                    if (initialiser != null && (constructorCall = initialiser.asInstanceOf(ConstructorCall.class)) != null) {
                        if (!constructorCall.parameterExpressions().isEmpty()) {
                            Guide.GuideGenerator args = Guide.defaultGuideGenerator();
                            outputBuilder.add(Symbol.LEFT_PARENTHESIS).add(args.start());
                            boolean firstParam = true;
                            for (Expression expression : constructorCall.parameterExpressions()) {
                                if (firstParam) {
                                    firstParam = false;
                                } else {
                                    outputBuilder.add(Symbol.COMMA).add(args.mid());
                                }
                                outputBuilder.add(expression.output(qualification));
                            }
                            outputBuilder.add(args.end()).add(Symbol.RIGHT_PARENTHESIS);
                        }
                    } else if (initialiser != null) {
                        throw new UnsupportedOperationException("Expect initialiser to be a NewObject");
                    }
                }
            }
            outputBuilder.add(gg.end()).add(Symbol.SEMICOLON);
            return Stream.of(outputBuilder);
        }
        return Stream.of();
    }


    record ResultOfImportComputation(Set<String> imports, QualificationImpl qualification) {
    }

    private static class PerPackage {
        final List<TypeInfo> types = new LinkedList<>();
        boolean allowStar = true;
    }

    private static ResultOfImportComputation imports(String myPackage, TypeInfo typeInfo) {
        Set<TypeInfo> typesReferenced = typeInfo.typesReferenced().stream().filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .filter(TypeInfo::allowInImport)
                .collect(Collectors.toSet());
        Map<String, PerPackage> typesPerPackage = new HashMap<>();
        QualificationImpl qualification = new QualificationImpl();
        typesReferenced.forEach(ti -> {
            String packageName = ti.packageName();
            if (packageName != null && !myPackage.equals(packageName)) {
                boolean doImport = qualification.addTypeReturnImport(ti);
                PerPackage perPackage = typesPerPackage.computeIfAbsent(packageName, p -> new PerPackage());
                if (doImport) {
                    perPackage.types.add(ti);
                } else {
                    perPackage.allowStar = false; // because we don't want to play with complicated ordering
                }
            }
        });
        // IMPROVE static fields and methods
        Set<String> imports = new TreeSet<>();
        for (Map.Entry<String, PerPackage> e : typesPerPackage.entrySet()) {
            PerPackage perPackage = e.getValue();
            if (perPackage.types.size() >= 4 && perPackage.allowStar) {
                imports.add(e.getKey() + ".*");
            } else {
                for (TypeInfo ti : perPackage.types) {
                    imports.add(ti.fullyQualifiedName);
                }
            }
        }
        return new ResultOfImportComputation(imports, qualification);
    }
}
