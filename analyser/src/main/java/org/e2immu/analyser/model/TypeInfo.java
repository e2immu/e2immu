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

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.IsMyself;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.OutputTypeInfo;
import org.e2immu.analyser.output.TypeName;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.analyser.util.UpgradableIntMap;
import org.e2immu.annotation.NotNull;
import org.e2immu.support.Either;
import org.e2immu.support.SetOnce;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class TypeInfo implements NamedType,
        InfoObject,
        WithInspectionAndAnalysis, Comparable<TypeInfo> {

    public static final String JAVA_LANG_OBJECT = "java.lang.Object";
    public static final String IS_FACT_FQN = "org.e2immu.annotatedapi.AnnotatedAPI.isFact(boolean)";
    public static final String IS_KNOWN_FQN = "org.e2immu.annotatedapi.AnnotatedAPI.isKnown(boolean)";
    public static Map<String, HardCoded> HARDCODED_TYPES = Collections.unmodifiableMap(new HashMap<>() {{
        put("java.lang.Annotation", HardCoded.IMMUTABLE_HC);
        put("java.lang.Enum", HardCoded.IMMUTABLE_HC);
        put("java.lang.Object", HardCoded.IMMUTABLE_HC);
        put("java.io.Serializable", HardCoded.IMMUTABLE_HC);
        put("java.util.Comparator", HardCoded.IMMUTABLE_HC);
        put("java.util.Optional", HardCoded.IMMUTABLE_HC_INDEPENDENT_HC);

        put("java.lang.CharSequence", HardCoded.IMMUTABLE_HC);
        put("java.lang.Class", HardCoded.IMMUTABLE);
        put("java.lang.Module", HardCoded.IMMUTABLE);
        put("java.lang.Package", HardCoded.IMMUTABLE);
        put("java.lang.constant.Constable", HardCoded.IMMUTABLE_HC);
        put("java.lang.constant.ConstantDesc", HardCoded.IMMUTABLE_HC);

        put("java.util.Map", HardCoded.MUTABLE_CONTAINER_DO_NOT_ERASE); // ClassValue
        put("java.util.AbstractMap", HardCoded.MUTABLE_CONTAINER_DO_NOT_ERASE); // ClassValue
        put("java.util.WeakHashMap", HardCoded.MUTABLE_CONTAINER_DO_NOT_ERASE); // ClassValue
        put("java.lang.ref.WeakReference", HardCoded.MUTABLE_CONTAINER_DO_NOT_ERASE); // ClassValue
        put("java.util.Collection", HardCoded.MUTABLE_CONTAINER_DO_NOT_ERASE); //  companion
        put("java.lang.Throwable", HardCoded.MUTABLE_NOT_CONTAINER_DO_NOT_ERASE);

        put("org.e2immu.annotatedapi.AnnotatedAPI", HardCoded.IMMUTABLE_HC);

        // primitives, boxed
        put("java.lang.Boolean", HardCoded.IMMUTABLE);
        put("java.lang.Byte", HardCoded.IMMUTABLE);
        put("java.lang.Character", HardCoded.IMMUTABLE);
        put("java.lang.Double", HardCoded.IMMUTABLE);
        put("java.lang.Float", HardCoded.IMMUTABLE);
        put("java.lang.Integer", HardCoded.IMMUTABLE);
        put("java.lang.Long", HardCoded.IMMUTABLE);
        put("java.lang.Short", HardCoded.IMMUTABLE);
        put("java.lang.String", HardCoded.IMMUTABLE);
        put("java.lang.Void", HardCoded.IMMUTABLE);
    }});

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

    public boolean isNumeric() {
        return isInt() || isInteger() ||
                isLong() || isBoxedLong() ||
                isShort() || isBoxedShort() ||
                isByte() || isBoxedByte() ||
                isFloat() || isBoxedFloat() ||
                isDouble() || isBoxedDouble();
    }

    public boolean isBoxedExcludingVoid() {
        return isBoxedByte() || isBoxedShort() || isInteger() || isBoxedLong()
                || isCharacter() || isBoxedFloat() || isBoxedDouble() || isBoxedBoolean();
    }

    public boolean allowInImport() {
        return isNotJavaLang() && !isPrimitiveExcludingVoid() && !isVoid();
    }

    public boolean packageIsExactlyJavaLang() {
        return "java.lang".equals(packageName());
    }

    public boolean isNotJavaLang() {
        return !this.fullyQualifiedName.startsWith("java.lang.");
    }

    public boolean needsParent() {
        return fullyQualifiedName.indexOf('.') > 0 && !fullyQualifiedName.startsWith("java.lang");
    }

    public boolean isJavaLangObject() {
        return JAVA_LANG_OBJECT.equals(this.fullyQualifiedName);
    }

    boolean isJavaLangString() {
        return "java.lang.String".equals(this.fullyQualifiedName);
    }

    boolean isJavaLangClass() {
        return "java.lang.Class".equals(this.fullyQualifiedName);
    }

    boolean isJavaLangVoid() {
        return "java.lang.Void".equals(this.fullyQualifiedName);
    }

    public boolean isVoid() {
        return "void".equals(this.fullyQualifiedName);
    }

    public boolean isBoxedFloat() {
        return "java.lang.Float".equals(this.fullyQualifiedName);
    }

    public boolean isFloat() {
        return "float".equals(this.fullyQualifiedName);
    }

    public boolean isBoxedDouble() {
        return "java.lang.Double".equals(this.fullyQualifiedName);
    }

    public boolean isDouble() {
        return "double".equals(this.fullyQualifiedName);
    }

    public boolean isBoxedByte() {
        return "java.lang.Byte".equals(this.fullyQualifiedName);
    }

    public boolean isByte() {
        return "byte".equals(this.fullyQualifiedName);
    }

    public boolean isBoxedShort() {
        return "java.lang.Short".equals(this.fullyQualifiedName);
    }

    public boolean isShort() {
        return "short".equals(this.fullyQualifiedName);
    }

    public boolean isBoxedLong() {
        return "java.lang.Long".equals(this.fullyQualifiedName);
    }

    public boolean isLong() {
        return "long".equals(this.fullyQualifiedName);
    }

    public boolean isBoxedBoolean() {
        return "java.lang.Boolean".equals(this.fullyQualifiedName);
    }

    public boolean isChar() {
        return "char".equals(this.fullyQualifiedName);
    }

    public boolean isInteger() {
        return "java.lang.Integer".equals(this.fullyQualifiedName);
    }

    public boolean isInt() {
        return "int".equals(this.fullyQualifiedName);
    }

    public boolean isBoolean() {
        return "boolean".equals(this.fullyQualifiedName);
    }

    public boolean isCharacter() {
        return "java.lang.Character".equals(this.fullyQualifiedName);
    }

    public boolean isPrimitiveExcludingVoid() {
        return this.isByte() || this.isShort() || this.isInt() || this.isLong() ||
                this.isChar() || this.isFloat() || this.isDouble() || this.isBoolean();
    }

    public boolean isJavaIoSerializable() {
        return "java.io.Serializable".equals(fullyQualifiedName);
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
        this(Identifier.generate("type"), packageName, simpleName);
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
        this(Identifier.generate("subtype"), enclosingType, simpleName);
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
        return isAbstract(InspectionProvider.DEFAULT);
    }

    public boolean isAbstract(InspectionProvider inspectionProvider) {
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        return inspection.isAbstract();
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
        ParameterizedType parentClass = inspection.parentClass();
        if (parentClass.isJavaLangObject()) return this;
        return parentClass.typeInfo.topOfInterdependentClassHierarchy();
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

    public boolean parentIsNotJavaLangObject() {
        ParameterizedType parentClass = typeInspection.get().parentClass();
        return parentClass != null && !parentClass.isJavaLangObject();
    }

    public ParameterizedType asParameterizedType(InspectionProvider inspectionProvider) {
        List<ParameterizedType> typeParameters = inspectionProvider.getTypeInspection(this).typeParameters()
                .stream().map(TypeParameter::toParameterizedType)
                .collect(Collectors.toList());
        return new ParameterizedType(this, typeParameters);
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


    public boolean isEnclosedIn(TypeInfo typeInfo) {
        if (typeInfo == this) return true;
        if (packageNameOrEnclosingType.isLeft()) return false;
        return packageNameOrEnclosingType.getRight().isEnclosedIn(typeInfo);
    }

    // FIXME this needs a better implementation: we want exactly those types that are equal to or subtypes of the argument,
    // but not appearing in the statements of methods or initializers of fields (they have a different primary type)
    public boolean isEnclosedInStopAtLambdaOrAnonymous(TypeInfo typeInfo) {
        if (typeInfo == this) return true;
        if (packageNameOrEnclosingType.isLeft()) return false;
        if (isAnonymous() || isClassInMethod()) return false;
        return packageNameOrEnclosingType.getRight().isEnclosedIn(typeInfo);
    }

    public boolean isPrivateNested() {
        return isNestedType() && typeInspection.get().isPrivate();
    }

    public boolean isPrivateOrEnclosingIsPrivate() {
        if (typeInspection.get().isPrivate()) return true;
        if (packageNameOrEnclosingType.isLeft()) return false;
        return packageNameOrEnclosingType.getRight().isPrivateOrEnclosingIsPrivate();
    }

    public boolean isInterface() {
        return isInterface(InspectionProvider.DEFAULT);
    }

    public boolean isInterface(InspectionProvider inspectionProvider) {
        return inspectionProvider.getTypeInspection(this).typeNature() == TypeNature.INTERFACE;
    }

    public boolean isEventual() {
        return typeAnalysis.get().isEventual();
    }

    public MethodInfo findUniqueMethod(String methodName, int parameters) {
        return typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(m -> m.name.equals(methodName) && m.methodInspection.get().getParameters().size() == parameters)
                .findAny().orElseThrow();
    }

    public MethodInfo findUniqueMethod(String methodName, TypeInfo typeOfFirstParameter) {
        return typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(m -> m.name.equals(methodName) && m.methodInspection.get().getParameters().size() > 0)
                .filter(m -> typeOfFirstParameter.equals(m.methodInspection.get().getParameters().get(0)
                        .parameterizedType.typeInfo))
                .findAny().orElseThrow(() -> new IllegalArgumentException(
                        "Cannot find unique method with first parameter type " + typeOfFirstParameter.fullyQualifiedName
                                + " in " + fullyQualifiedName));
    }

    public MethodInfo findUniqueMethod(InspectionProvider inspectionProvider, String methodName, int parameters) {
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        return inspection.methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .filter(m -> m.name.equals(methodName) &&
                        inspectionProvider.getMethodInspection(m).getParameters().size() == parameters)
                .findAny().orElseThrow();
    }

    /*
    we ignore shallowly analysed methods that are not public.
    This avoids the length() issue in StringBuilder AbstractStringBuilder CharSequence;
    where the method has been annotated in CharSequence; the implementation in AbstractStringBuilder is
    unknown, inaccessible and not annotated.
     */
    public MethodInfo findMethodImplementing(MethodInfo abstractMethodInfo) {
        if (abstractMethodInfo.typeInfo == this) return null;
        MethodInfo foundHere = typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> m.methodResolution.get().overrides().contains(abstractMethodInfo))
                .findFirst().orElse(null);
        if (foundHere != null && !foundHere.methodInspection.get().isAbstract()
                && (foundHere.computedAnalysis() || foundHere.methodInspection.get().isPubliclyAccessible())) {
            return foundHere;
        }
        TypeInspection inspection = typeInspection.get();
        ParameterizedType parentClass = inspection.parentClass();
        if (parentClass != null && !parentClass.isJavaLangObject()) {
            MethodInfo foundInParent = parentClass.typeInfo.findMethodImplementing(abstractMethodInfo);
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

    public MethodInfo findOverriddenSingleAbstractMethod(InspectionProvider inspectionProvider) {
        return typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)
                .map(inspectionProvider::getMethodInspection)
                .filter(mi -> !mi.isDefault() && !mi.isStatic())
                .findFirst().orElseThrow().getMethodInfo();
    }

    public MethodInfo findConstructor(int parameters) {
        return typeInspection.get().constructors().stream()
                .filter(c -> c.methodInspection.get().getParameters().size() == parameters)
                .findFirst().orElseThrow();
    }

    public MethodInfo findConstructor(TypeInfo typeOfFirstParameter) {
        return typeInspection.get().constructors().stream()
                .filter(c -> c.methodInspection.get().getParameters().size() > 0)
                .filter(c -> typeOfFirstParameter.equals(c.methodInspection.get()
                        .getParameters().get(0).parameterizedType.bestTypeInfo()))
                .findFirst().orElseThrow();
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
        if (!typeInspection.isSet()) return UpgradableBooleanMap.of();
        TypeInspection inspection = typeInspection.get();

        UpgradableBooleanMap<TypeInfo> fromParent = inspection.parentClass() == null ? UpgradableBooleanMap.of()
                : inspection.parentClass().typesReferenced(true);

        UpgradableBooleanMap<TypeInfo> enclosingType = packageNameOrEnclosingType.isRight() && !isStatic() && !isInterface() ?
                UpgradableBooleanMap.of(packageNameOrEnclosingType.getRight(), false) : UpgradableBooleanMap.of();

        UpgradableBooleanMap<TypeInfo> fromInterfaces = inspection.interfacesImplemented().stream()
                .flatMap(i -> i.typesReferenced(true).stream()).collect(UpgradableBooleanMap.collector());

        UpgradableBooleanMap<TypeInfo> inspectedAnnotations = inspection.getAnnotations().stream()
                .flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());

        UpgradableBooleanMap<TypeInfo> analysedAnnotations = hasBeenAnalysed() ? typeAnalysis.get().getAnnotationStream()
                .flatMap(entry -> entry.getKey().typesReferenced().stream()).collect(UpgradableBooleanMap.collector())
                : UpgradableBooleanMap.of();

        return UpgradableBooleanMap.of(
                fromParent,
                enclosingType,
                fromInterfaces,
                inspectedAnnotations,
                analysedAnnotations,

                // types from methods and constructors and their parameters
                inspection.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                        .flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),

                // types from fields
                inspection.fields().stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()),

                // types from subTypes
                inspection.subTypes().stream().flatMap(a -> a.typesReferenced().stream()).collect(UpgradableBooleanMap.collector())

        );
    }

    public static final int PARENT_WEIGHT = 100_000;
    public static final int INTERFACE_WEIGHT = 10_000;
    public static final int ENCLOSING_WEIGHT = 10_000;
    public static final int ANNOTATION_WEIGHT = 10;

    @Override
    public UpgradableIntMap<TypeInfo> typesReferenced2() {
        if (!typeInspection.isSet()) return UpgradableIntMap.of();
        TypeInspection inspection = typeInspection.get();

        UpgradableIntMap<TypeInfo> fromParent = inspection.parentClass() == null ? UpgradableIntMap.of()
                : inspection.parentClass().typesReferenced2(PARENT_WEIGHT);

        UpgradableIntMap<TypeInfo> fromInterfaces = inspection.interfacesImplemented().stream()
                .flatMap(i -> i.typesReferenced2(INTERFACE_WEIGHT).stream())
                .collect(UpgradableIntMap.collector());

        UpgradableIntMap<TypeInfo> inspectedAnnotations = inspection.getAnnotations().stream()
                .flatMap(a -> a.typesReferenced2(ANNOTATION_WEIGHT).stream()).collect(UpgradableIntMap.collector());

        UpgradableIntMap<TypeInfo> analysedAnnotations = hasBeenAnalysed() ? typeAnalysis.get().getAnnotationStream()
                .flatMap(entry -> entry.getKey().typesReferenced2(ANNOTATION_WEIGHT).stream()).collect(UpgradableIntMap.collector())
                : UpgradableIntMap.of();

        return UpgradableIntMap.of(
                fromParent,
                fromInterfaces,
                inspectedAnnotations,
                analysedAnnotations,

                // types from methods and constructors and their parameters
                inspection.methodsAndConstructors(TypeInspection.Methods.THIS_TYPE_ONLY)
                        .flatMap(a -> a.typesReferenced2().stream()).collect(UpgradableIntMap.collector()),

                // types from fields
                inspection.fields().stream().flatMap(a -> a.typesReferenced2().stream()).collect(UpgradableIntMap.collector()),

                // types from subTypes
                inspection.subTypes().stream().flatMap(a -> a.typesReferenced2().stream()).collect(UpgradableIntMap.collector())
        );
    }

    public Map<NamedType, ParameterizedType> mapInTermsOfParametersOfSuperType(InspectionProvider inspectionProvider, ParameterizedType superType) {
        assert superType.typeInfo != this;
        TypeInspection ti = inspectionProvider.getTypeInspection(this);
        if (ti.parentClass() != null) {
            if (ti.parentClass().typeInfo == superType.typeInfo) {
                Map<NamedType, ParameterizedType> forward = superType.forwardTypeParameterMap(inspectionProvider);
                Map<NamedType, ParameterizedType> formal = ti.parentClass().initialTypeParameterMap(inspectionProvider);
                return combineMaps(forward, formal);
            }
            Map<NamedType, ParameterizedType> map = ti.parentClass().typeInfo.mapInTermsOfParametersOfSuperType(inspectionProvider, superType);
            if (map != null) {
                return combineMaps(ti.parentClass().initialTypeParameterMap(inspectionProvider), map);
            }
        }
        for (ParameterizedType implementedInterface : ti.interfacesImplemented()) {
            if (implementedInterface.typeInfo == superType.typeInfo) {
                Map<NamedType, ParameterizedType> forward = superType.forwardTypeParameterMap(inspectionProvider);
                Map<NamedType, ParameterizedType> formal = implementedInterface.initialTypeParameterMap(inspectionProvider);
                return combineMaps(formal, forward);
            }
            Map<NamedType, ParameterizedType> map = implementedInterface.typeInfo.mapInTermsOfParametersOfSuperType(inspectionProvider, superType);
            if (map != null) {
                return combineMaps(implementedInterface.initialTypeParameterMap(inspectionProvider), map);
            }
        }
        return null; // not in this branch of the recursion
    }

    // practically the duplicate of the previous, except that we should parameterize initialTypeParameterMap as well to collapse them
    public Map<NamedType, ParameterizedType> mapInTermsOfParametersOfSubType(InspectionProvider inspectionProvider, ParameterizedType superType) {
        assert superType.typeInfo != this;
        TypeInspection ti = inspectionProvider.getTypeInspection(this);
        if (ti.parentClass() != null) {
            if (ti.parentClass().typeInfo == superType.typeInfo) {
                return ti.parentClass().forwardTypeParameterMap(inspectionProvider);
            }
            Map<NamedType, ParameterizedType> map = ti.parentClass().typeInfo.mapInTermsOfParametersOfSubType(inspectionProvider, superType);
            if (map != null) {
                return combineMaps(map, ti.parentClass().forwardTypeParameterMap(inspectionProvider));
            }
        }
        for (ParameterizedType implementedInterface : ti.interfacesImplemented()) {
            if (implementedInterface.typeInfo == superType.typeInfo) {
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
        // NOTE as of Java 21: java.lang.Compiler has no parent class
        List<FieldInfo> fromParent = this.isJavaLangObject() || inspection.parentClass() == null ? List.of() :
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
        if (parentClass != null && !parentClass.isJavaLangObject()) {
            TypeInfo res = parentClass.typeInfo.recursivelyImplements(inspectionProvider, fqn);
            if (res != null) return res;
        }
        for (ParameterizedType implemented : inspection.interfacesImplemented()) {
            TypeInfo res = implemented.typeInfo.recursivelyImplements(inspectionProvider, fqn);
            if (res != null) return res;
        }
        return null;
    }

    public boolean parentalHierarchyContains(TypeInfo target, InspectionProvider inspectionProvider) {
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        ParameterizedType parent = inspection.parentClass();
        if (parent == null || parent.isJavaLangObject()) return false;
        if (target.equals(parent.typeInfo)) return true;
        return parent.typeInfo.parentalHierarchyContains(target, inspectionProvider);
    }

    public boolean nonStaticallyEnclosingTypesContains(TypeInfo target, InspectionProvider inspectionProvider) {
        if (packageNameOrEnclosingType.isLeft()) return false;
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        if (inspection.isStatic()) return false;
        TypeInfo enclosing = packageNameOrEnclosingType.getRight();
        if (enclosing.equals(target)) return true;
        return enclosing.nonStaticallyEnclosingTypesContains(target, inspectionProvider);
    }

    @Override
    public int compareTo(TypeInfo o) {
        return fullyQualifiedName.compareTo(o.fullyQualifiedName);
    }

    @Override
    public Location newLocation() {
        return new LocationImpl(this);
    }

    @Override
    public MethodInfo getMethodInfo() {
        return null;
    }

    @Override
    public CausesOfDelay delay(CauseOfDelay.Cause cause) {
        return DelayFactory.createDelay(newLocation(), cause);
    }

    // in other words, cannot be subclassed
    public boolean isFinal(InspectionProvider inspectionProvider) {
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        return inspection.modifiers().contains(TypeModifier.FINAL) || inspection.typeNature().isFinal();
    }

    public boolean isAggregated() {
        return isInterface() && (typeInspection.get().isSealed() || typeResolution.get().hasOneKnownGeneratedImplementation());
    }

    public IsMyself isMyself(ParameterizedType type, InspectionProvider inspectionProvider) {
        TypeInfo bestType = type.bestTypeInfo(inspectionProvider);
        return isMyself(bestType, inspectionProvider);
    }

    public IsMyself isMyself(TypeInfo bestType, InspectionProvider inspectionProvider) {
        if (bestType == null) return IsMyself.NO;
        if (equals(bestType)) return IsMyself.YES;
        TypeInfo primaryVariable = bestType.primaryType();
        TypeInfo primaryMyself = primaryType();
        if (primaryMyself.equals(primaryVariable)) {
            TypeInspection primaryVariableInspection = inspectionProvider.getTypeInspection(primaryVariable);
            if (primaryVariableInspection.isInterface()) return IsMyself.NO;

            // in the same compilation unit, analysed at the same time
            boolean inHierarchy = bestType.parentalHierarchyContains(this, inspectionProvider) ||
                    parentalHierarchyContains(bestType, inspectionProvider) ||
                    bestType.nonStaticallyEnclosingTypesContains(this, inspectionProvider) ||
                    nonStaticallyEnclosingTypesContains(bestType, inspectionProvider);
            if (inHierarchy) return IsMyself.YES;
            // must be symmetrical: see e.g. Basics_24
            if (typeResolution.get().fieldsAccessedInRestOfPrimaryType()
                    || bestType.typeResolution.get().fieldsAccessedInRestOfPrimaryType()) {
                return IsMyself.PTA;
            }
        }
        return IsMyself.NO;
    }


    public TypeName typeName(TypeName.Required requiresQualifier) {
        String fqn = packageIsExactlyJavaLang() ? simpleName : fullyQualifiedName;
        return new TypeName(simpleName, fqn, isPrimaryType() ? simpleName : fromPrimaryTypeDownwards(),
                requiresQualifier);
    }

    public TypeInfo firstStaticEnclosingType(InspectionProvider inspectionProvider) {
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        if (inspection.isStatic()) return this;
        assert !isPrimaryType();
        return packageNameOrEnclosingType.getRight().firstStaticEnclosingType(inspectionProvider);
    }

    public Set<ParameterizedType> superTypes(InspectionProvider inspectionProvider) {
        TypeInspection inspection = inspectionProvider.getTypeInspection(this);
        Set<ParameterizedType> superTypes = new HashSet<>();
        if (inspection.parentClass() != null) {
            superTypes.add(inspection.parentClass());
            if (!inspection.parentClass().isJavaLangObject()) {
                superTypes.addAll(inspection.parentClass().typeInfo.superTypes(inspectionProvider));
            }
        }
        for (ParameterizedType interfaceImplemented : inspection.interfacesImplemented()) {
            superTypes.add(interfaceImplemented);
            superTypes.addAll(interfaceImplemented.typeInfo.superTypes(inspectionProvider));
        }
        return superTypes;
    }

    private static final Pattern ANONYMOUS = Pattern.compile("\\$\\d+");

    private static final Pattern CLASS_IN_METHOD = Pattern.compile("\\$KV\\$");

    public boolean isAnonymous() {
        return ANONYMOUS.matcher(simpleName).matches();
    }

    public boolean isClassInMethod() {
        return CLASS_IN_METHOD.matcher(simpleName).find();
    }

    public boolean recursivelyInConstructionOrStaticWithRespectTo(InspectionProvider inspectionProvider,
                                                                  TypeInfo enclosingType) {
        MethodInfo enclosingMethod = inspectionProvider.getTypeInspection(this).enclosingMethod();
        if (enclosingMethod != null) {
            if (enclosingMethod.inConstruction()) return true;
            MethodInspection enclosingInspection = inspectionProvider.getMethodInspection(enclosingMethod);
            if (enclosingMethod.typeInfo == enclosingType && enclosingInspection.isStatic()) {
                return true;
            }
            return enclosingMethod.typeInfo
                    .recursivelyInConstructionOrStaticWithRespectTo(inspectionProvider, enclosingType);
        }
        return false;
    }

    public enum HardCoded {
        IMMUTABLE(true), IMMUTABLE_HC(true), IMMUTABLE_HC_INDEPENDENT_HC(true),
        MUTABLE_NOT_CONTAINER_DO_NOT_ERASE(false),
        MUTABLE_CONTAINER_DO_NOT_ERASE(false),
        NO(false);

        public final boolean eraseDependencies;

        HardCoded(boolean eraseDependencies) {
            this.eraseDependencies = eraseDependencies;
        }
    }
}
