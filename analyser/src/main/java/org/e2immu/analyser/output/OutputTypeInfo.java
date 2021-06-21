package org.e2immu.analyser.output;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OutputTypeInfo {
    public static OutputBuilder output(TypeInfo typeInfo, Qualification qualification, boolean doTypeDeclaration) {
        String typeNature;
        Set<String> imports;
        QualificationImpl insideType;
        if (typeInfo.isPrimaryType() && typeInfo.hasBeenInspected()) {
            ResultOfImportComputation res = imports(typeInfo.packageName(), typeInfo.typeInspection.get());
            imports = res.imports;
            insideType = res.qualification;
        } else {
            imports = Set.of();
            insideType = typeInfo.hasBeenInspected() ? new QualificationImpl(qualification) : new QualificationImpl();
        }
        assert insideType != null;

        String[] typeModifiers;
        List<FieldInfo> fields;
        List<MethodInfo> constructors;
        List<MethodInfo> methods;
        List<TypeInfo> subTypes;
        List<ParameterizedType> interfaces;
        List<TypeParameter> typeParameters;
        ParameterizedType parentClass;
        boolean isInterface;
        boolean isRecord;

        if (typeInfo.hasBeenInspected()) {
            TypeInspection typeInspection = typeInfo.typeInspection.get();
            typeNature = typeInspection.typeNature().toJava();
            isInterface = typeInspection.isInterface();
            isRecord = typeInspection.typeNature() == TypeNature.RECORD;
            typeModifiers = TypeModifier.sort(typeInspection.modifiers());
            fields = typeInspection.fields();
            constructors = typeInspection.constructors();
            methods = typeInspection.methods();
            subTypes = typeInspection.subTypes();
            typeParameters = typeInspection.typeParameters();
            parentClass = typeInfo.parentIsNotJavaLangObject() ? typeInspection.parentClass() : null;
            interfaces = typeInspection.interfacesImplemented();

            // add the methods that we can call without having to qualify (method() instead of super.method())
            addMethodsToQualification(typeInfo, insideType);
            addThisToQualification(typeInfo, insideType);
        } else {
            typeNature = "class"; // we really have no idea what it is
            typeModifiers = new String[]{"abstract"};
            fields = List.of();
            constructors = List.of();
            methods = List.of();
            subTypes = List.of();
            typeParameters = List.of();
            interfaces = List.of();
            parentClass = null;
            isInterface = false;
            isRecord = false;
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
                    .add(Arrays.stream(typeModifiers).map(mod -> new OutputBuilder().add(new Text(mod)))
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
                afterAnnotations.add(outputFieldsAsParameters(insideType, fields));
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
        return packageAndImports.add(Stream.concat(annotationStream, Stream.of(afterAnnotations))
                .collect(OutputBuilder.joining(Space.ONE_REQUIRED_EASY_SPLIT,
                        Guide.generatorForAnnotationList())));
    }

    private static OutputBuilder outputFieldsAsParameters(Qualification qualification, List<FieldInfo> fields) {
        return fields.stream()
                .map(fieldInfo -> fieldInfo.output(qualification, true))
                .collect(OutputBuilder.joining(Symbol.COMMA, Symbol.LEFT_PARENTHESIS, Symbol.RIGHT_PARENTHESIS,
                        Guide.generatorForParameterDeclaration()));
    }

    private static void addThisToQualification(TypeInfo typeInfo, QualificationImpl insideType) {
        insideType.addThis(new This(InspectionProvider.DEFAULT, typeInfo));
        if (!Primitives.isJavaLangObject(typeInfo.typeInspection.get().parentClass())) {
            insideType.addThis(new This(InspectionProvider.DEFAULT, typeInfo.typeInspection.get().parentClass().typeInfo,
                    null, true));
        }
    }

    private static void addMethodsToQualification(TypeInfo typeInfo, QualificationImpl qImpl) {
        TypeInspection ti = typeInfo.typeInspection.get("Inspection of type " + typeInfo.fullyQualifiedName);
        ti.methods().forEach(qImpl::addMethodUnlessOverride);
        if (!Primitives.isJavaLangObject(typeInfo)) {
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
                    NewObject newObject;
                    if (initialiser != null && (newObject = initialiser.asInstanceOf(NewObject.class)) != null) {
                        if (!newObject.parameterExpressions().isEmpty()) {
                            Guide.GuideGenerator args = Guide.defaultGuideGenerator();
                            outputBuilder.add(Symbol.LEFT_PARENTHESIS).add(args.start());
                            boolean firstParam = true;
                            for (Expression expression : newObject.parameterExpressions()) {
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

    private static ResultOfImportComputation imports(String myPackage, TypeInspection typeInspection) {
        Set<TypeInfo> typesReferenced = typeInspection.typesReferenced().stream().filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .filter(Primitives::allowInImport)
                .collect(Collectors.toSet());
        Map<String, PerPackage> typesPerPackage = new HashMap<>();
        QualificationImpl qualification = new QualificationImpl();
        typesReferenced.forEach(typeInfo -> {
            String packageName = typeInfo.packageName();
            if (packageName != null && !myPackage.equals(packageName)) {
                boolean doImport = qualification.addTypeReturnImport(typeInfo);
                PerPackage perPackage = typesPerPackage.computeIfAbsent(packageName, p -> new PerPackage());
                if (doImport) {
                    perPackage.types.add(typeInfo);
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
                for (TypeInfo typeInfo : perPackage.types) {
                    imports.add(typeInfo.fullyQualifiedName);
                }
            }
        }
        return new ResultOfImportComputation(imports, qualification);
    }
}
