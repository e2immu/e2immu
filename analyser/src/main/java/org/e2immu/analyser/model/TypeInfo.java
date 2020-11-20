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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.model.expression.MethodCall;
import org.e2immu.analyser.model.expression.MethodReference;
import org.e2immu.analyser.model.expression.TypeExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.ExpressionAsStatement;
import org.e2immu.analyser.model.statement.ReturnStatement;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.*;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.LAMBDA;
import static org.e2immu.analyser.util.Logger.log;

public class TypeInfo implements NamedType, WithInspectionAndAnalysis {

    @NotNull
    public final String simpleName;
    @NotNull
    public final String fullyQualifiedName;

    //@Immutable(after="this.inspect()")
    public final SetOnce<TypeInspection> typeInspection = new SetOnce<>();
    public final SetOnce<TypeResolution> typeResolution = new SetOnce<>();
    public final SetOnce<TypeAnalysis> typeAnalysis = new SetOnce<>();

    // creates an anonymous version of the parent type parameterizedType
    public TypeInfo(TypeInfo enclosingType, int number) {
        this(enclosingType.fullyQualifiedName + "$" + number, enclosingType.simpleName + "$" + number);
    }

    @Override
    public void setAnalysis(Analysis analysis) {
        typeAnalysis.set((TypeAnalysis) analysis);
    }

    @Override
    public String name() {
        return simpleName;
    }

    public static TypeInfo createFqnOrPackageNameDotSimpleName(@NotNull String packageName, @NotNull String simpleName) {
        if (Objects.requireNonNull(packageName).isEmpty())
            throw new UnsupportedOperationException("Expect a non-empty package name for " + simpleName);
        return new TypeInfo(packageName + "." + simpleName, simpleName);
    }

    public static TypeInfo fromFqn(@NotNull String fullyQualifiedName) {
        int dot = fullyQualifiedName.lastIndexOf('.');
        String simpleName;
        if (dot >= 0) {
            simpleName = fullyQualifiedName.substring(dot + 1);
        } else {
            simpleName = fullyQualifiedName;
        }
        return new TypeInfo(fullyQualifiedName, simpleName);
    }

    private TypeInfo(String fullyQualifiedName, String simpleName) {
        this.simpleName = Objects.requireNonNull(simpleName);
        this.fullyQualifiedName = Objects.requireNonNull(fullyQualifiedName);
    }

    @Override
    public Inspection getInspection() {
        return typeInspection.get();
    }

    @Override
    public String fullyQualifiedName() {
        return fullyQualifiedName;
    }

    public boolean hasBeenInspected() {
        return typeInspection.isSet();
    }

    public Stream<TypeInfo> accessibleBySimpleNameTypeInfoStream() {
        return accessibleBySimpleNameTypeInfoStream(this, new HashSet<>());
    }

    private Stream<TypeInfo> accessibleBySimpleNameTypeInfoStream(TypeInfo startingPoint, Set<TypeInfo> visited) {
        if (visited.contains(this)) return Stream.empty();
        visited.add(this);
        Stream<TypeInfo> mySelf = Stream.of(this);

        TypeInspection typeInspection = this.typeInspection.get();
        boolean inSameCompilationUnit = this == startingPoint || primaryType() == startingPoint.primaryType();
        boolean inSamePackage = !inSameCompilationUnit &&
                primaryType().typeInspection.get().packageNameOrEnclosingType.getLeft().equals(
                        startingPoint.primaryType().typeInspection.get().packageNameOrEnclosingType.getLeft());

        Stream<TypeInfo> localStream = typeInspection.subTypes
                .stream()
                .filter(typeInfo -> inSameCompilationUnit ||
                        typeInfo.typeInspection.get().access == TypeModifier.PUBLIC ||
                        inSamePackage && typeInfo.typeInspection.get().access == TypeModifier.PACKAGE ||
                        !inSamePackage && typeInfo.typeInspection.get().access == TypeModifier.PROTECTED);
        Stream<TypeInfo> parentStream;
        if (!Primitives.isJavaLangObject(typeInspection.parentClass)) {
            assert typeInspection.parentClass.typeInfo != null;
            parentStream = typeInspection.parentClass.typeInfo.accessibleBySimpleNameTypeInfoStream(startingPoint, visited);
        } else parentStream = Stream.empty();

        Stream<TypeInfo> joint = Stream.concat(Stream.concat(mySelf, localStream), parentStream);
        for (ParameterizedType interfaceType : typeInspection.interfacesImplemented) {
            assert interfaceType.typeInfo != null;
            Stream<TypeInfo> fromInterface = interfaceType.typeInfo.accessibleBySimpleNameTypeInfoStream(startingPoint, visited);
            joint = Stream.concat(joint, fromInterface);
        }
        return joint;
    }

    public Stream<FieldInfo> accessibleFieldsStream() {
        return accessibleFieldsStream(this);
    }

    private Stream<FieldInfo> accessibleFieldsStream(TypeInfo startingPoint) {
        TypeInspection typeInspection = this.typeInspection.get("Inspection of " + fullyQualifiedName);
        boolean inSameCompilationUnit = this == startingPoint || primaryType() == startingPoint.primaryType();
        boolean inSamePackage = !inSameCompilationUnit &&
                primaryType().typeInspection.get().packageNameOrEnclosingType.getLeft().equals(
                        startingPoint.primaryType().typeInspection.get().packageNameOrEnclosingType.getLeft());

        // my own field
        Stream<FieldInfo> localStream = typeInspection.fields
                .stream()
                .filter(fieldInfo -> inSameCompilationUnit ||
                        fieldInfo.fieldInspection.get().getAccess() == FieldModifier.PUBLIC ||
                        inSamePackage && fieldInfo.fieldInspection.get().getAccess() == FieldModifier.PACKAGE ||
                        !inSamePackage && fieldInfo.fieldInspection.get().getAccess() == FieldModifier.PROTECTED);

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
        if (!Primitives.isJavaLangObject(typeInspection.parentClass)) {
            assert typeInspection.parentClass.typeInfo != null;
            parentStream = typeInspection.parentClass.typeInfo.accessibleFieldsStream(startingPoint);
        } else parentStream = Stream.empty();
        joint = Stream.concat(joint, parentStream);

        // my interfaces' fields
        for (ParameterizedType interfaceType : typeInspection.interfacesImplemented) {
            assert interfaceType.typeInfo != null;
            Stream<FieldInfo> fromInterface = interfaceType.typeInfo.accessibleFieldsStream(startingPoint);
            joint = Stream.concat(joint, fromInterface);
        }

        return joint;
    }

    public String computePackageName() {
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
            TypeInspection typeInspection = this.typeInspection.get();
            typeNature = typeInspection.typeNature.toJava();
            typeModifiers = typeInspection.modifiers.stream().map(TypeModifier::toJava);
            packageName = typeInspection.packageNameOrEnclosingType.getLeftOrElse("");
            annotations.addAll(typeInspection.getAnnotations());
            fields = typeInspection.fields;
            constructors = typeInspection.constructors;
            methods = typeInspection.methods;
            subTypes = typeInspection.subTypes;
            parentClass = parentIsNotJavaLangObject() ? typeInspection.parentClass.stream() : "";
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
                imports.stream().sorted().forEach(i ->
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
            typeAnalysis.get().getAnnotationStream().forEach(entry -> {
                boolean present = entry.getValue();
                AnnotationExpression annotation = entry.getKey();
                if (present && !annotationsSeen.contains(annotation.typeInfo())) {
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

    private Set<String> imports() {
        Set<TypeInfo> typesReferenced = typesReferenced().stream().filter(Map.Entry::getValue)
                .filter(e -> !e.getKey().isJavaLang())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        Map<String, List<TypeInfo>> perPackage = new HashMap<>();
        String myPackage = packageName();
        typesReferenced.forEach(typeInfo -> {
            String packageName = typeInfo.packageName();
            if (packageName != null && !myPackage.equals(packageName)) {
                SMapList.add(perPackage, packageName, typeInfo);
            }
        });
        Set<String> result = new TreeSet<>();
        for (Map.Entry<String, List<TypeInfo>> e : perPackage.entrySet()) {
            List<TypeInfo> list = e.getValue();
            if (list.size() >= 4) {
                result.add(e.getKey() + ".*");
            } else {
                for (TypeInfo typeInfo : list) {
                    result.add(typeInfo.fullyQualifiedName);
                }
            }
        }
        return result;
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

    public boolean isJavaLang() {
        if (Primitives.isPrimitiveExcludingVoid(this)) return true;
        return Primitives.JAVA_LANG.equals(packageName());
    }

    /**
     * This is the starting place to compute all types that are referred to in any way.
     * It is different from imports, because imports need an explicitly written type.
     *
     * @return a map of all types referenced, with the boolean indicating explicit reference somewhere
     */
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        TypeInspection ti = typeInspection.get();
        return UpgradableBooleanMap.of(
                ti.parentClass.typesReferenced(true),
                ti.packageNameOrEnclosingType.isRight() && !isStatic() && !isInterface() ?
                        UpgradableBooleanMap.of(ti.packageNameOrEnclosingType.getRight(), false) :
                        UpgradableBooleanMap.of(),
                ti.interfacesImplemented.stream().flatMap(i -> i.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector()),
                ti.getAnnotations().stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),
                //ti.subTypes.stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),
                ti.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                        .flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),
                ti.fields.stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector())
        );
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
    public Optional<AnnotationExpression> hasInspectedAnnotation(Class<?> annotation) {
        if (!typeInspection.isSet()) return Optional.empty();
        String annotationFQN = annotation.getName();
        Optional<AnnotationExpression> fromType = (getInspection().getAnnotations().stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(annotationFQN)))
                .findFirst();
        if (fromType.isPresent()) return fromType;
        if (parentIsNotJavaLangObject()) {
            Optional<AnnotationExpression> fromParent = Objects.requireNonNull(typeInspection.get().parentClass.typeInfo).hasInspectedAnnotation(annotation);
            if (fromParent.isPresent()) return fromParent;
        }
        return Optional.empty();
    }

    public Optional<TypeInfo> inTypeInnerOuterHierarchy(TypeInfo typeInfo) {
        return inTypeInnerOuterHierarchy(typeInfo, new HashSet<>());
    }

    public boolean parentIsNotJavaLangObject() {
        return !Primitives.isJavaLangObject(typeInspection.get().parentClass);
    }

    private Optional<TypeInfo> inTypeInnerOuterHierarchy(TypeInfo typeInfo, Set<TypeInfo> visited) {
        if (typeInfo == this) return Optional.of(this);
        if (visited.contains(this)) return Optional.empty();
        visited.add(this);
        if (typeInspection.get().packageNameOrEnclosingType.isRight()) {
            TypeInfo parentClass = typeInspection.get().packageNameOrEnclosingType.getRight();
            Optional<TypeInfo> viaParent = parentClass.inTypeInnerOuterHierarchy(typeInfo, visited);
            if (viaParent.isPresent()) return viaParent;
        }
        for (TypeInfo subType : typeInspection.get().subTypes) {
            Optional<TypeInfo> viaSubType = subType.inTypeInnerOuterHierarchy(typeInfo, visited);
            if (viaSubType.isPresent()) return viaSubType;
        }
        return Optional.empty();
    }

    public ParameterizedType asParameterizedType() {
        if (!typeInspection.isSet()) {
            return new ParameterizedType(this, List.of());
        }
        return new ParameterizedType(this, typeInspection.get().typeParameters
                .stream().map(tp -> new ParameterizedType(tp, 0, ParameterizedType.WildCard.NONE)).collect(Collectors.toList()));
    }

    public boolean isStatic() {
        if (!typeInspection.isSet()) throw new UnsupportedOperationException();
        if (typeInspection.get().packageNameOrEnclosingType.isLeft()) return true; // independent type
        return typeInspection.get().modifiers.contains(TypeModifier.STATIC); // static sub type
    }

    /**
     * Find a method, given a translation map
     *
     * @param target         the method to find (typically from a sub type)
     * @param translationMap from the type parameters of this to the concrete types of the sub-type
     * @return the method of this, if deemed the same
     */
    private MethodInfo findUniqueMethod(MethodInfo target, Map<NamedType, ParameterizedType> translationMap) {
        for (MethodInfo methodInfo : typeInspection.get(fullyQualifiedName).methodsAndConstructors()) {
            if (methodInfo.sameMethod(target, translationMap)) {
                return methodInfo;
            }
        }
        return null;
    }

    public List<ParameterizedType> directSuperTypes() {
        if (Primitives.isJavaLangObject(this)) return List.of();
        List<ParameterizedType> list = new ArrayList<>();
        list.add(typeInspection.get().parentClass);
        list.addAll(typeInspection.get().interfacesImplemented);
        return list;
    }

    public List<TypeInfo> superTypesExcludingJavaLangObject() {
        if (Primitives.isJavaLangObject(this)) return List.of();
        if (typeInspection.get().superTypes.isSet())
            return typeInspection.get().superTypes.get();
        List<TypeInfo> list = new ArrayList<>();
        ParameterizedType parentPt = typeInspection.get().parentClass;
        TypeInfo parent;
        boolean parentIsNotJLO = !Primitives.isJavaLangObject(parentPt);
        if (parentIsNotJLO) {
            parent = Objects.requireNonNull(parentPt.typeInfo);
            list.add(parent);
            list.addAll(parent.superTypesExcludingJavaLangObject());
        }

        typeInspection.get().interfacesImplemented.forEach(i -> {
            list.add(i.typeInfo);
            assert i.typeInfo != null;
            list.addAll(i.typeInfo.superTypesExcludingJavaLangObject());
        });
        List<TypeInfo> immutable = ImmutableList.copyOf(list);
        typeInspection.get().superTypes.set(immutable);
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
            Set<MethodInfo> myOverrides = ourOwnLevel ? typeInspection.get().overrides.getOtherwiseNull(methodInfo) : null;
            if (myOverrides != null) return myOverrides;
        }
        Set<MethodInfo> result = recursiveOverridesCall(methodInfo, Map.of());
        Set<MethodInfo> immutable = ImmutableSet.copyOf(result);
        if (ourOwnLevel && cacheResult) {
            typeInspection.get().overrides.put(methodInfo, immutable);
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
                assert superType.typeInfo != null;
                ParameterizedType formalType = superType.typeInfo.asParameterizedType();
                translationMapOfSuperType = new HashMap<>(translationMap);
                int index = 0;
                for (ParameterizedType parameter : formalType.parameters) {
                    ParameterizedType concreteParameter = superType.parameters.get(index);
                    translationMapOfSuperType.put(parameter.typeParameter, concreteParameter);
                    index++;
                }
            }
            assert superType.typeInfo != null;
            MethodInfo override = superType.typeInfo.findUniqueMethod(methodInfo, translationMapOfSuperType);
            if (override != null) {
                result.add(override);
            }
            if (!Primitives.isJavaLangObject(superType.typeInfo)) {
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
        return typeInspection.get().methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(methodInfo -> methodInfo.distinguishingName().equals(distinguishingName))
                .findFirst().orElse(null);
    }

    public FieldInfo getFieldByName(String name, boolean complain) {
        Optional<FieldInfo> result = typeInspection.get().fields.stream().filter(fieldInfo -> fieldInfo.name.equals(name)).findFirst();
        return complain ? result.orElseThrow() : result.orElse(null);
    }

    public TypeInfo primaryType() {
        if (typeInspection.isSet()) {
            Either<String, TypeInfo> packageNameOrEnclosingType = typeInspection.get().packageNameOrEnclosingType;
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
                                                       ExpressionContext expressionContext) {
        MethodTypeParameterMap method = functionalInterfaceType.findSingleAbstractMethodOfInterface();
        TypeInfo typeInfo = new TypeInfo(enclosingType, expressionContext.topLevel.newIndex(enclosingType));
        TypeInspection.TypeInspectionBuilder builder = new TypeInspection.TypeInspectionBuilder();
        builder.setEnclosingType(this);
        builder.setTypeNature(TypeNature.CLASS);
        builder.addInterfaceImplemented(functionalInterfaceType);
        builder.setParentClass(expressionContext.typeContext.getPrimitives().objectParameterizedType);

        // there are no extra type parameters; only those of the enclosing type(s) can be in 'type'

        MethodInfo methodInfo = method.buildCopy(typeInfo);
        builder.addMethod(methodInfo);

        // compose the content of the method...

        Expression newReturnExpression;
        if (methodReference.methodInfo.isStatic || !(methodReference.scope instanceof TypeExpression)) {
            newReturnExpression = methodCallCopyAllParameters(methodReference.scope, methodReference.methodInfo, methodInfo);
        } else {
            if (methodInfo.methodInspection.get().getParameters().size() != 1)
                throw new UnsupportedOperationException("Referenced method has multiple parameters");
            newReturnExpression = methodCallNoParameters(methodInfo, methodReference.methodInfo);
        }
        Statement statement;
        if (methodInfo.isVoid()) {
            statement = new ExpressionAsStatement(newReturnExpression);
        } else {
            statement = new ReturnStatement(false, newReturnExpression);
        }
        Block block = new Block.BlockBuilder().addStatement(statement).build();

        log(LAMBDA, "Result of translating block: {}", block.statementString(0, null));
        methodInfo.methodInspection.get().getMethodBody().set(block);
        typeInfo.typeInspection.set(builder.build(typeInfo));
        expressionContext.addNewlyCreatedType(typeInfo);
        return methodInfo;
    }


    private Expression methodCallNoParameters(MethodInfo interfaceMethod, MethodInfo concreteMethod) {
        Expression newScope = new VariableExpression(interfaceMethod.methodInspection.get().getParameters().get(0));
        MethodTypeParameterMap methodTypeParameterMap = new MethodTypeParameterMap(concreteMethod, Map.of());
        return new MethodCall(newScope, newScope, methodTypeParameterMap, List.of());
    }

    private Expression methodCallCopyAllParameters(Expression scope, MethodInfo concreteMethod, MethodInfo interfaceMethod) {
        List<Expression> parameterExpressions = interfaceMethod.methodInspection.get()
                .getParameters().stream().map(VariableExpression::new).collect(Collectors.toList());
        Map<NamedType, ParameterizedType> concreteTypes = new HashMap<>();
        int i = 0;
        for (ParameterInfo parameterInfo : concreteMethod.methodInspection.get().getParameters()) {
            ParameterInfo interfaceParameter = interfaceMethod.methodInspection.get().getParameters().get(i);
            if (interfaceParameter.parameterizedType.isTypeParameter()) {
                concreteTypes.put(interfaceParameter.parameterizedType.typeParameter, parameterInfo.parameterizedType);
            }
            i++;
        }
        MethodTypeParameterMap methodTypeParameterMap = new MethodTypeParameterMap(concreteMethod, concreteTypes);
        return new MethodCall(scope, scope, methodTypeParameterMap, parameterExpressions);
    }

    public boolean isNestedType() {
        return typeInspection.get().packageNameOrEnclosingType.isRight();
    }

    public List<TypeInfo> allTypesInPrimaryType() {
        return primaryType().typeInspection.get().allTypesInPrimaryType;
    }

    public boolean isPrivate() {
        return typeInspection.get().modifiers.contains(TypeModifier.PRIVATE);
    }

    public boolean isAnEnclosingTypeOf(TypeInfo typeInfo) {
        if (typeInfo == this) return true;
        if (typeInfo.typeInspection.get().packageNameOrEnclosingType.isLeft()) return false;
        return isAnEnclosingTypeOf(typeInfo.typeInspection.get().packageNameOrEnclosingType.getRight());
    }

    public boolean isRecord() {
        return isNestedType() && isPrivate();
    }

    public boolean isInterface() {
        return typeInspection.get().typeNature == TypeNature.INTERFACE;
    }

    public boolean isFunctionalInterface() {
        if (typeInspection.get("isFunctionalInterface on " + fullyQualifiedName).typeNature != TypeNature.INTERFACE) {
            return false;
        }
        return typeInspection.get().getAnnotations().stream()
                .anyMatch(ann -> Primitives.isFunctionalInterfaceAnnotation(ann.typeInfo()));
    }

    public Set<ObjectFlow> objectFlows(AnalysisProvider analysisProvider) {
        Set<ObjectFlow> result = new HashSet<>(analysisProvider.getTypeAnalysis(this).getConstantObjectFlows());
        for (MethodInfo methodInfo : typeInspection.get().methodsAndConstructors()) {
            // set, because the returned object flow could equal either one of the non-returned, or parameter flows
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().getParameters()) {
                result.add(analysisProvider.getParameterAnalysis(parameterInfo).getObjectFlow());
            }
            MethodAnalysis methodAnalysis = analysisProvider.getMethodAnalysis(methodInfo);
            result.addAll(methodAnalysis.getInternalObjectFlows());

            if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                result.add(methodAnalysis.getObjectFlow());
            }
        }
        // for fields we only add those owned by the field itself (i.e. with an initialiser)
        for (FieldInfo fieldInfo : typeInspection.get().fields) {
            FieldAnalysis fieldAnalysis = analysisProvider.getFieldAnalysis(fieldInfo);
            ObjectFlow objectFlow = fieldAnalysis.getObjectFlow();
            if (objectFlow != null && objectFlow.location.info == fieldInfo) {
                result.add(objectFlow);
            }
            result.addAll(fieldAnalysis.getInternalObjectFlows());
        }
        for (TypeInfo subType : typeInspection.get().subTypes) {
            result.addAll(subType.objectFlows(analysisProvider));
        }
        return result;
    }

    public boolean isEventual() {
        return typeAnalysis.get().isEventual();
    }

    public MethodInfo findUniqueMethod(String methodName, int parameters) {
        return typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM).
                filter(m -> m.name.equals(methodName) && m.methodInspection.get().getParameters().size() == parameters)
                .findAny().orElseThrow();
    }

    public Set<ParameterizedType> typesOfMethodsAndConstructors() {
        Set<ParameterizedType> result = new HashSet<>();
        for (MethodInfo methodInfo : typeInspection.get().methodsAndConstructors()) {
            if (!methodInfo.isConstructor && !methodInfo.isVoid()) {
                result.add(methodInfo.returnType());
            }
            for (ParameterInfo parameterInfo : methodInfo.methodInspection.get().getParameters()) {
                result.add(parameterInfo.parameterizedType);
            }
        }
        return result;
    }

    public String packageName() {
        if (!typeInspection.isSet()) {
            // it's too late now
            return computePackageName();
        }
        if (typeInspection.get().packageNameOrEnclosingType.isLeft())
            return typeInspection.get().packageNameOrEnclosingType.getLeft();
        return typeInspection.get().packageNameOrEnclosingType.getRight().packageName();
    }

    // this type implements a functional interface, and we need to find the single abstract method
    public MethodInfo findOverriddenSingleAbstractMethod() {
        return typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(mi -> !mi.isDefaultImplementation && !mi.isStatic)
                .findFirst().orElseThrow();
    }

    public MethodInfo findConstructor(int parameters) {
        return typeInspection.get().constructors.stream()
                .filter(c -> c.methodInspection.get().getParameters().size() == parameters)
                .findFirst().orElseThrow();
    }

    public boolean isPrimaryType() {
        return typeInspection.isSet() && typeInspection.get().packageNameOrEnclosingType.isLeft();
    }

    /**
     * Analysis
     *
     * @return true when we can completely bypass the analysers using the "copyAnnotationsIntoTypeAnalysisProperties" method
     */
    public boolean shallowAnalysis() {
        if (!typeInspection.isSet()) throw new UnsupportedOperationException();
        TypeInspection inspection = typeInspection.get();
        // we don't analyse annotations at the moment
        if (inspection.typeNature == TypeNature.ANNOTATION) return true;
        return inspection.methodsAndConstructors(TypeInspection.Methods.INCLUDE_SUBTYPES).allMatch(MethodInfo::shallowAnalysis);
    }
}
