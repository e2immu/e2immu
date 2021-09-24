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

package org.e2immu.analyser.model;

import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.OutputTypeInfo;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.Either;
import org.e2immu.support.SetOnce;

import java.util.*;
import java.util.stream.Collectors;

public class TypeInfo implements NamedType, WithInspectionAndAnalysis {

    @NotNull
    public final String simpleName;
    @NotNull
    public final String fullyQualifiedName;

    public final Identifier identifier;

    // when this type is an inner or nested class of an enclosing class
    public final Either<String, TypeInfo> packageNameOrEnclosingType;

    //@Immutable(after="this.inspect()")
    public final SetOnce<TypeInspection> typeInspection = new SetOnce<>();
    public final SetOnce<TypeResolution> typeResolution = new SetOnce<>();
    public final SetOnce<TypeAnalysis> typeAnalysis = new SetOnce<>();

    // creates an anonymous version of the parent type parameterizedType
    public TypeInfo(TypeInfo enclosingType, int number) {
        this(enclosingType, "$" + number);
    }

    @Override
    public void setAnalysis(Analysis analysis) {
        typeAnalysis.set((TypeAnalysis) analysis);
    }

    @Override
    public Analysis getAnalysis() {
        return typeAnalysis.get();
    }

    @Override
    public boolean hasBeenAnalysed() {
        return typeAnalysis.isSet();
    }

    @Override
    public String name() {
        return simpleName;
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }

    public TypeInfo(String packageName, String simpleName) {
        this(Identifier.generate(), packageName, simpleName);
    }

    public TypeInfo(Identifier identifier, String packageName, String simpleName) {
        assert packageName != null && !packageName.isBlank();
        assert simpleName != null && !simpleName.isBlank();
        this.identifier = identifier;
        this.simpleName = Objects.requireNonNull(simpleName);
        this.packageNameOrEnclosingType = Either.left(packageName);
        if (Primitives.JAVA_PRIMITIVE.equals(packageName)) {
            this.fullyQualifiedName = simpleName;
        } else {
            this.fullyQualifiedName = packageName + "." + simpleName;
        }
    }

    public TypeInfo(TypeInfo enclosingType, String simpleName) {
        this(Identifier.generate(), enclosingType, simpleName);
    }

    public TypeInfo(Identifier identifier, TypeInfo enclosingType, String simpleName) {
        this.simpleName = Objects.requireNonNull(simpleName);
        this.packageNameOrEnclosingType = Either.right(enclosingType);
        this.fullyQualifiedName = enclosingType.fullyQualifiedName + "." + simpleName;
        this.identifier = identifier;
    }

    @Override
    public TypeInfo getTypeInfo() {
        return this;
    }

    @Override
    public Inspection getInspection() {
        return typeInspection.get();
    }

    @Override
    public String fullyQualifiedName() {
        return fullyQualifiedName;
    }

    @Override
    public boolean hasBeenInspected() {
        return typeInspection.isSet();
    }

    public OutputBuilder output() {
        assert isPrimaryType();
        return output(null, true);
    }

    public OutputBuilder output(Qualification qualification, boolean doTypeDeclaration) {
        return OutputTypeInfo.output(this, qualification, doTypeDeclaration);
    }

    public Set<FieldInfo> findFields(InspectionProvider inspectionProvider, String csv) {
        if (csv.isBlank()) {
            return Set.of();
        }
        List<FieldInfo> fields = visibleFields(inspectionProvider);
        return Arrays.stream(csv.split(",")).filter(s -> !s.isBlank()).map(s ->
                        fields.stream().filter(f -> f.name.equals(s))
                                .findFirst()
                                .orElseThrow(() -> new UnsupportedOperationException("Cannot find field " + s + " in type " + fullyQualifiedName)))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAbstract() {
        TypeInspection inspection = typeInspection.get();
        if (inspection.typeNature() == TypeNature.INTERFACE) return true;
        return inspection.modifiers().contains(TypeModifier.ABSTRACT);
    }

    /*
    they'll be waiting on each other to define IMMUTABLE
     */
    public TypeInfo topOfInterdependentClassHierarchy() {
        TypeInspection inspection = typeInspection.get();
        if (inspection.isStatic()) return this;
        // first go to enclosing type
        if (packageNameOrEnclosingType.isRight()) {
            return packageNameOrEnclosingType.getRight().topOfInterdependentClassHierarchy();
        }
        // or to parent type, but only if in the same file TODO
        if (Primitives.isJavaLangObject(inspection.parentClass())) return this;
        return inspection.parentClass().typeInfo.topOfInterdependentClassHierarchy();
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
            Optional<AnnotationExpression> fromParent = Objects.requireNonNull(typeInspection.get().parentClass().typeInfo)
                    .hasInspectedAnnotation(annotation);
            if (fromParent.isPresent()) return fromParent;
        }
        return Optional.empty();
    }

    public boolean parentIsNotJavaLangObject() {
        return !Primitives.isJavaLangObject(typeInspection.get().parentClass());
    }

    public ParameterizedType asParameterizedType(InspectionProvider inspectionProvider) {
        return new ParameterizedType(this, inspectionProvider.getTypeInspection(this).typeParameters()
                .stream().map(tp -> new ParameterizedType(tp, 0, ParameterizedType.WildCard.NONE)).collect(Collectors.toList()));
    }
    // to be called before type inspection has been built

    public ParameterizedType asSimpleParameterizedType() {
        return new ParameterizedType(this, List.of());
    }

    public boolean isStatic() {
        assert typeInspection.isSet();
        return typeInspection.get().isStatic();
    }


    @Override
    public String toString() {
        return fullyQualifiedName;
    }

    public FieldInfo getFieldByName(String name, boolean complain) {
        Optional<FieldInfo> result = typeInspection.get().fields().stream().filter(fieldInfo -> fieldInfo.name.equals(name)).findFirst();
        return complain ? result.orElseThrow(() -> new IllegalArgumentException("No field known with name " + name)) :
                result.orElse(null);
    }

    public TypeInfo primaryType() {
        if (packageNameOrEnclosingType.isLeft()) return this;
        return packageNameOrEnclosingType.getRight().primaryType();
    }

    public boolean isNestedType() {
        return packageNameOrEnclosingType.isRight();
    }

    public boolean isPrivate() {
        return typeInspection.get().modifiers().contains(TypeModifier.PRIVATE);
    }

    public boolean isPublic() {
        if (!typeInspection.get().modifiers().contains(TypeModifier.PUBLIC)) return false;
        if (packageNameOrEnclosingType.isRight()) {
            return packageNameOrEnclosingType.getRight().isPublic();
        }
        return true;
    }

    public boolean isEnclosedIn(TypeInfo typeInfo) {
        if (typeInfo == this) return true;
        if (packageNameOrEnclosingType.isLeft()) return false;
        return packageNameOrEnclosingType.getRight().isEnclosedIn(typeInfo);
    }

    public boolean isPrivateNested() {
        return isNestedType() && isPrivate();
    }

    public boolean isPrivateOrEnclosingIsPrivate() {
        if (isPrivate()) return true;
        if (packageNameOrEnclosingType.isLeft()) return false;
        return packageNameOrEnclosingType.getRight().isPrivateOrEnclosingIsPrivate();
    }

    public boolean isInterface() {
        return typeInspection.get().typeNature() == TypeNature.INTERFACE;
    }

    public boolean isEventual() {
        return typeAnalysis.get().isEventual();
    }

    public MethodInfo findUniqueMethod(String methodName, int parameters) {
        return typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(m -> m.name.equals(methodName) && m.methodInspection.get().getParameters().size() == parameters)
                .findAny().orElseThrow();
    }

    public MethodInfo findUniqueMethod(InspectionProvider inspectionProvider, String methodName, int parameters) {
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        return inspection.methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(m -> m.name.equals(methodName) &&
                        inspectionProvider.getMethodInspection(m).getParameters().size() == parameters)
                .findAny().orElseThrow();
    }

    public MethodInfo findMethodImplementing(MethodInfo abstractMethodInfo) {
        if (abstractMethodInfo.typeInfo == this) return null;
        MethodInfo foundHere = typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> m.methodResolution.get().overrides().contains(abstractMethodInfo))
                .findFirst().orElse(null);
        if (foundHere != null && !foundHere.isAbstract()) return foundHere;
        TypeInspection inspection = typeInspection.get();
        if (!Primitives.isJavaLangObject(inspection.parentClass())) {
            MethodInfo foundInParent = inspection.parentClass().typeInfo.findMethodImplementing(abstractMethodInfo);
            if (foundInParent != null) return foundInParent;
        }
        for (ParameterizedType interfaceType : inspection.interfacesImplemented()) {
            MethodInfo foundInInterface = interfaceType.typeInfo.findMethodImplementing(abstractMethodInfo);
            if (foundInInterface != null) return foundInInterface;
        }
        return null;
    }

    public String packageName() {
        if (packageNameOrEnclosingType.isLeft())
            return packageNameOrEnclosingType.getLeft();
        return packageNameOrEnclosingType.getRight().packageName();
    }
    // this type implements a functional interface, and we need to find the single abstract method

    public MethodInfo findOverriddenSingleAbstractMethod() {
        return typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .map(mi -> mi.methodInspection.get())
                .filter(mi -> !mi.isDefault() && !mi.isStatic())
                .findFirst().orElseThrow().getMethodInfo();
    }

    public MethodInfo findConstructor(int parameters) {
        return typeInspection.get().constructors().stream()
                .filter(c -> c.methodInspection.get().getParameters().size() == parameters)
                .findFirst().orElseThrow();
    }


    public MethodInfo findConstructor(InspectionProvider inspectionProvider, List<Expression> parameterExpressions) {
        return findConstructor(inspectionProvider, parameterExpressions, true);
    }

    private MethodInfo findConstructor(InspectionProvider inspectionProvider,
                                       List<Expression> parameterExpressions,
                                       boolean allowPrivate) {
        MethodInfo constructor = typeInspection.get().constructors().stream()
                .filter(mi -> allowPrivate || !mi.isPrivate(inspectionProvider))
                .filter(mi -> compatibleParameters(inspectionProvider, parameterExpressions, mi))
                .findFirst().orElse(null);
        if (constructor != null) return constructor;
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        if (inspection.parentClass() != null) {
            return inspection.parentClass().typeInfo.findConstructor(inspectionProvider, parameterExpressions, false);
        }
        throw new UnsupportedOperationException("Could not find correct constructor");
    }

    private boolean compatibleParameters(InspectionProvider inspectionProvider,
                                         List<Expression> parameterExpressions,
                                         MethodInfo methodInfo) {
        MethodInspection inspection = inspectionProvider.getMethodInspection(methodInfo);
        int nFormal = inspection.getParameters().size();
        if (nFormal == parameterExpressions.size() || nFormal > 0 && nFormal < parameterExpressions.size()
                && inspection.getParameters().get(nFormal - 1).parameterInspection.get().isVarArgs()) {
            int i = 0;
            for (ParameterInfo parameterInfo : inspection.getParameters()) {
                Expression expression = parameterExpressions.get(i);
                ParameterizedType formalType = parameterInfo.parameterizedType;
                ParameterizedType concreteType = expression.returnType();
                if (!formalType.isAssignableFrom(inspectionProvider, concreteType)) {
                    return false;
                }
                i++;
            }
            return true;
        }
        return false;
    }

    public boolean isPrimaryType() {
        return packageNameOrEnclosingType.isLeft();
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
        if (inspection.typeNature() == TypeNature.ANNOTATION) return true;
        return !inspection.inspector().statements();
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        if (!typeInspection.isSet()) return UpgradableBooleanMap.of(); // dangerous?
        return typeInspection.get("type inspection of " + fullyQualifiedName).typesReferenced();
    }

    public Map<NamedType, ParameterizedType> mapInTermsOfParametersOfSuperType(InspectionProvider inspectionProvider, TypeInfo superType) {
        assert superType != this;
        TypeInspection ti = inspectionProvider.getTypeInspection(this);
        if (ti.parentClass() != null) {
            if (ti.parentClass().typeInfo == superType) {
                return ti.parentClass().initialTypeParameterMap(inspectionProvider);
            }
            Map<NamedType, ParameterizedType> map = ti.parentClass().typeInfo.mapInTermsOfParametersOfSuperType(inspectionProvider, superType);
            if (map != null) {
                return combineMaps(ti.parentClass().initialTypeParameterMap(inspectionProvider), map);
            }
        }
        for (ParameterizedType implementedInterface : ti.interfacesImplemented()) {
            if (implementedInterface.typeInfo == superType) {
                return implementedInterface.initialTypeParameterMap(inspectionProvider);
            }
            Map<NamedType, ParameterizedType> map = implementedInterface.typeInfo.mapInTermsOfParametersOfSuperType(inspectionProvider, superType);
            if (map != null) {
                return combineMaps(implementedInterface.initialTypeParameterMap(inspectionProvider), map);
            }
        }
        return null; // not in this branch of the recursion
    }

    // practically the duplicate of the previous, except that we should parameterize initialTypeParameterMap as well to collapse them
    public Map<NamedType, ParameterizedType> mapInTermsOfParametersOfSubType(InspectionProvider inspectionProvider, TypeInfo superType) {
        assert superType != this;
        TypeInspection ti = inspectionProvider.getTypeInspection(this);
        if (ti.parentClass() != null) {
            if (ti.parentClass().typeInfo == superType) {
                return ti.parentClass().forwardTypeParameterMap(inspectionProvider);
            }
            Map<NamedType, ParameterizedType> map = ti.parentClass().typeInfo.mapInTermsOfParametersOfSubType(inspectionProvider, superType);
            if (map != null) {
                return combineMaps(map, ti.parentClass().forwardTypeParameterMap(inspectionProvider));
            }
        }
        for (ParameterizedType implementedInterface : ti.interfacesImplemented()) {
            if (implementedInterface.typeInfo == superType) {
                return implementedInterface.forwardTypeParameterMap(inspectionProvider);
            }
            Map<NamedType, ParameterizedType> map = implementedInterface.typeInfo.mapInTermsOfParametersOfSubType(inspectionProvider, superType);
            if (map != null) {
                return combineMaps(map, implementedInterface.forwardTypeParameterMap(inspectionProvider));
            }
        }
        return null; // not in this branch of the recursion
    }

    /*
    StringMap<V> -> HashMap<K,V> -> Map<K, V>

    M2: K(map) -> K(hashmap), M1: K(hashmap) -> String
     */
    public static Map<NamedType, ParameterizedType> combineMaps(Map<NamedType, ParameterizedType> m1, Map<NamedType, ParameterizedType> m2) {
        assert m1 != null;
        return m2.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,
                e -> e.getValue().isTypeParameter() ? m1.getOrDefault(e.getValue().typeParameter, e.getValue()) : e.getValue(),
                (v1, v2) -> {
                    throw new UnsupportedOperationException();
                }, LinkedHashMap::new));
    }

    public int countEnumConstants() {
        assert typeInspection.get().typeNature() == TypeNature.ENUM;
        return (int) typeInspection.get().fields().stream().filter(fieldInfo -> fieldInfo.fieldInspection.get().isSynthetic()).count();
    }

    public String fromPrimaryTypeDownwards() {
        if (packageNameOrEnclosingType.isLeft()) {
            return simpleName;
        }
        return packageNameOrEnclosingType.getRight().fromPrimaryTypeDownwards() + "." + simpleName;
    }

    public List<FieldInfo> visibleFields(InspectionProvider inspectionProvider) {
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        List<FieldInfo> locally = inspection.fields();
        List<FieldInfo> fromParent = Primitives.isJavaLangObject(this) ? List.of() :
                inspection.parentClass().typeInfo.visibleFields(inspectionProvider);
        List<FieldInfo> fromInterfaces = inspection.interfacesImplemented().stream()
                .flatMap(i -> i.typeInfo.visibleFields(inspectionProvider).stream()).toList();
        return ListUtil.immutableConcat(locally, fromParent, fromInterfaces);
    }

    public boolean typePropertiesAreContracted() {
        TypeInspection inspection = typeInspection.get();
        if (inspection.isInterface()) {
            return !inspection.isSealed() && !typeResolution.get().hasOneKnownGeneratedImplementation();
        }
        return false;
    }

    @Override
    public String niceClassName() {
        if (typeInspection.isSet()) {
            TypeNature typeNature = typeInspection.get().typeNature();
            return switch (typeNature) {
                case CLASS -> "Class";
                case INTERFACE -> "Interface";
                case ENUM -> "Enum";
                case ANNOTATION -> "Annotation";
                case RECORD -> "Record";
                default -> "Type";
            };
        }
        return "Type";
    }

    public TypeInfo recursivelyImplements(InspectionProvider inspectionProvider, String fqn) {
        if (fullyQualifiedName.equals(fqn)) return this;
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        ParameterizedType parentClass = inspection.parentClass();
        if (parentClass != null && !Primitives.isJavaLangObject(parentClass)) {
            TypeInfo res = parentClass.typeInfo.recursivelyImplements(inspectionProvider, fqn);
            if (res != null) return res;
        }
        for (ParameterizedType implemented : inspection.interfacesImplemented()) {
            TypeInfo res = implemented.typeInfo.recursivelyImplements(inspectionProvider, fqn);
            if (res != null) return res;
        }
        return null;
    }

    /*
    this and otherSubType share the same primary type; we know nothing more than that
     */
    public boolean hasAccessToFieldsOf(InspectionProvider inspectionProvider, TypeInfo otherSubType) {
        if (this == otherSubType) return true;
        List<TypeInfo> parentClasses = parentClasses(inspectionProvider);
        if (parentClasses.contains(otherSubType)) return true;
        List<TypeInfo> otherParentClasses = otherSubType.parentClasses(inspectionProvider);
        return otherParentClasses.contains(this);
    }

    private List<TypeInfo> parentClasses(InspectionProvider inspectionProvider) {
        TypeInfo start = this;
        List<TypeInfo> result = new ArrayList<>();
        while (true) {
            TypeInspection inspection = inspectionProvider.getTypeInspection(start);
            ParameterizedType parent = inspection.parentClass();
            if (parent != null && !Primitives.isJavaLangObject(parent)) {
                result.add(parent.typeInfo);
                start = parent.typeInfo;
            } else break;
        }
        return result;
    }

    public boolean hasAsParentClass(InspectionProvider inspectionProvider, TypeInfo target) {
        if (target == this) return true;
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        ParameterizedType parent = inspection.parentClass();
        if (parent != null) {
            if (Primitives.isJavaLangObject(parent)) {
                return target == parent.typeInfo;
            } else {
                return parent.typeInfo.hasAsParentClass(inspectionProvider, target);
            }
        }
        return false;
    }
}
