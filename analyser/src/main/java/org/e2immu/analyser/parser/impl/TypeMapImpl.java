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

package org.e2immu.analyser.parser.impl;

import com.github.javaparser.ParseException;
import org.e2immu.analyser.bytecode.OnDemandInspection;
import org.e2immu.analyser.inspector.InspectionState;
import org.e2immu.analyser.inspector.NotFoundInClassPathException;
import org.e2immu.analyser.inspector.TypeInspector;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectorImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.MemberValuePair;
import org.e2immu.analyser.model.impl.AnnotationExpressionImpl;
import org.e2immu.analyser.model.impl.TypeParameterImpl;
import org.e2immu.analyser.parser.*;
import org.e2immu.analyser.resolver.ShallowMethodResolver;
import org.e2immu.analyser.resolver.impl.ResolverImpl;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.Source;
import org.e2immu.analyser.util.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.e2immu.analyser.inspector.InspectionState.*;
import static org.e2immu.analyser.model.ParameterizedType.WildCard.NONE;

public class TypeMapImpl implements TypeMap {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeMapImpl.class);

    private final Trie<TypeInfo> trie;
    private final Primitives primitives;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;

    private TypeMapImpl(Trie<TypeInfo> trie, Primitives primitives, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.primitives = primitives;
        this.trie = trie;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        assert trie.isFrozen();
    }

    public static TypeInfo fromTrie(Trie<TypeInfo> sourceTypes, String[] split) {
        List<TypeInfo> upTo;
        int i = split.length;
        do {
            upTo = sourceTypes.get(split, i);
            i--;
        } while (i >= 1 && (upTo == null || upTo.isEmpty()));
        if (upTo != null && !upTo.isEmpty()) {
            TypeInfo typeInfo = upTo.get(0);
            for (int j = i + 1; j < split.length; i++) {
                typeInfo = new TypeInfo(typeInfo, split[j]);
                j++;
            }
            return typeInfo;
        }
        return null;
    }

    @Override
    public TypeInfo get(Class<?> clazz) {
        return get(clazz.getCanonicalName());
    }

    @Override
    public TypeInfo get(String fullyQualifiedName) {
        return get(trie, fullyQualifiedName);
    }

    public static TypeInfo get(Trie<TypeInfo> trie, String fullyQualifiedName) {
        String[] split = fullyQualifiedName.split("\\.");
        List<TypeInfo> typeInfoList = trie.get(split);
        return typeInfoList == null || typeInfoList.isEmpty() ? null : typeInfoList.get(0);
    }

    @Override
    public boolean isPackagePrefix(PackagePrefix packagePrefix) {
        return trie.isStrictPrefix(packagePrefix.prefix());
    }

    @Override
    public void visit(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer) {
        trie.visit(prefix, consumer);
    }

    @Override
    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return e2ImmuAnnotationExpressions;
    }

    @Override
    public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
        return fieldInfo.fieldInspection.get();
    }

    @Override
    public TypeInspection getTypeInspection(TypeInfo typeInfo) {
        return typeInfo.typeInspection.get();
    }

    @Override
    public MethodInspection getMethodInspection(MethodInfo methodInfo) {
        return methodInfo.methodInspection.get();
    }

    @Override
    public Primitives getPrimitives() {
        return primitives;
    }

    public static class Builder implements TypeMap.Builder {

        private final Trie<TypeInfo> trie;
        private final PrimitivesImpl primitives;
        private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
        private final Resources classPath;

        private final Map<TypeInfo, TypeInspection.Builder> typeInspections;
        private final Map<FieldInfo, FieldInspection.Builder> fieldInspections;
        private final Map<String, MethodInspection.Builder> methodInspections;

        private OnDemandInspection byteCodeInspector;
        private InspectWithJavaParser inspectWithJavaParser;

        public Builder(Resources resources) {
            trie = new Trie<>();
            primitives = new PrimitivesImpl();
            classPath = resources;
            e2ImmuAnnotationExpressions = new E2ImmuAnnotationExpressions();
            typeInspections = new HashMap<>();
            fieldInspections = new HashMap<>();
            methodInspections = new HashMap<>();

            for (TypeInfo typeInfo : getPrimitives().getTypeByName().values()) {
                if (!typeInfo.typeInspection.isSet())
                    add(typeInfo, TRIGGER_BYTECODE_INSPECTION);
            }
            for (TypeInfo typeInfo : getPrimitives().getPrimitiveByName().values()) {
                if (!typeInfo.typeInspection.isSet())
                    add(typeInfo, BY_HAND_WITHOUT_STATEMENTS);
            }
            e2ImmuAnnotationExpressions.streamTypes().forEach(typeInfo -> add(typeInfo, TRIGGER_BYTECODE_INSPECTION));
        }

        public Builder(TypeMapImpl.Builder source, Resources newClassPath) {
            classPath = newClassPath;
            trie = source.trie;
            primitives = source.primitives;
            e2ImmuAnnotationExpressions = source.e2ImmuAnnotationExpressions;
            typeInspections = source.typeInspections;
            fieldInspections = source.fieldInspections;
            methodInspections = source.methodInspections;
        }

        public TypeInfo loadType(String fqn, boolean complain) {
            TypeInfo inMap = get(fqn);
            if (inMap != null) return inMap;
            // we don't know it... so we don't know the boundary between primary and sub-type
            // we can either search in the class path, or in the source path

            Source path = classPath.fqnToPath(fqn, ".class");
            if (path == null) {
                if (complain) {
                    LOGGER.error("ERROR: Cannot find type '{}'", fqn);
                    throw new NotFoundInClassPathException(fqn);
                }
                return null;
            }
            return getOrCreateFromPathReturnInspection(path, TRIGGER_BYTECODE_INSPECTION).typeInfo();
        }

        public TypeMapImpl build() {
            trie.freeze();

            typeInspections.forEach((typeInfo, typeInspectionBuilder) -> {
                assert Input.acceptFQN(typeInfo.packageName());
                if (typeInspectionBuilder.finishedInspection() && !typeInfo.typeInspection.isSet()) {
                    typeInfo.typeInspection.set(typeInspectionBuilder.build(this));
                }
            });

            methodInspections.values().forEach(methodInspectionBuilder -> {
                MethodInfo methodInfo = methodInspectionBuilder.getMethodInfo();
                if (!methodInfo.methodInspection.isSet() && methodInfo.typeInfo.typeInspection.isSet()) {
                    methodInspectionBuilder.build(this); // will set the inspection itself
                }
            });
            fieldInspections.forEach((fieldInfo, fieldInspectionBuilder) -> {
                if (!fieldInfo.fieldInspection.isSet() && fieldInfo.owner.typeInspection.isSet()) {
                    fieldInfo.fieldInspection.set(fieldInspectionBuilder.build(this));
                }
            });

            // we make a new map, because the resolver will encounter new types (which we will ignore)
            // all methods not yet resolved, will be resolved here.
            new HashSet<>(typeInspections.keySet()).forEach(typeInfo -> {
                if (typeInfo.typeInspection.isSet()) {
                    if (!typeInfo.typeResolution.isSet()) {
                        Set<TypeInfo> superTypes = ResolverImpl.superTypesExcludingJavaLangObject(InspectionProvider.DEFAULT, typeInfo, null);
                        TypeResolution typeResolution = new TypeResolution.Builder()
                                .setSuperTypesExcludingJavaLangObject(superTypes)
                                .build();
                        typeInfo.typeResolution.set(typeResolution);
                    }
                    for (MethodInfo methodInfo : typeInfo.typeInspection.get().methodsAndConstructors()) {
                        if (!methodInfo.methodResolution.isSet()) {
                            methodInfo.methodResolution.set(ShallowMethodResolver.onlyOverrides(InspectionProvider.DEFAULT, methodInfo));
                        }
                    }
                }
            });

            return new TypeMapImpl(trie, primitives, e2ImmuAnnotationExpressions);
        }

        @Override
        public TypeInfo get(Class<?> clazz) {
            return get(clazz.getCanonicalName());
        }

        @Override
        public TypeInfo get(String fullyQualifiedName) {
            return TypeMapImpl.get(trie, fullyQualifiedName);
        }

        @Override
        public TypeInfo getOrCreateTriggerByteCode(String packageName, String simpleName) {
            Source source = classPath.fqnToPath(packageName + "." + simpleName, ".class");
            // example of not-accessible type: java.lang.Compiler
            Identifier id = source == null ? Identifier.NOT_ACCESSIBLE : Identifier.from(source.uri());
            return getOrCreate(packageName, simpleName, id, TRIGGER_BYTECODE_INSPECTION);
        }

        @Override
        public TypeInfo getOrCreate(String packageName, String simpleName, Identifier identifier, InspectionState inspectionState) {
            assert simpleName.indexOf('.') < 0; // no dots!
            TypeInfo typeInfo = get(packageName + "." + simpleName);
            if (typeInfo != null) {
                ensureTypeInspection(typeInfo, inspectionState);
                return typeInfo;
            }
            TypeInfo newType = new TypeInfo(identifier, packageName, simpleName);
            add(newType, inspectionState);
            return newType;
        }

        /*
        Creates types all the way up to the primary type if necessary
         */

        public TypeInspection.Builder getOrCreateFromPathReturnInspection(Source source,
                                                                          InspectionState inspectionState) {
            assert source != null;
            String path = source.stripDotClass();
            assert path.indexOf('.') < 0 : "Path is " + path; // no dots! uses / and $; the . is for the .class which should have been stripped
            int dollar = path.indexOf('$');
            TypeInfo primaryType = extractPrimaryTypeAndAddToMap(source, dollar);
            if (dollar < 0) return ensureTypeInspection(primaryType, inspectionState);
            TypeInfo enclosingType = primaryType;
            TypeInspection.Builder typeInspection = null;
            while (dollar >= 0) {
                int nextDollar = path.indexOf('$', dollar + 1);
                String simpleName = nextDollar < 0 ? path.substring(dollar + 1)
                        : path.substring(dollar + 1, nextDollar);
                String fqn = enclosingType.fullyQualifiedName + "." + simpleName;
                TypeInfo subTypeInMap = get(fqn);
                TypeInfo subType;
                if (subTypeInMap != null) {
                    subType = subTypeInMap;
                    typeInspection = ensureTypeInspection(subType, inspectionState);
                } else {
                    subType = new TypeInfo(Identifier.from(source.uri()), enclosingType, simpleName);
                    typeInspection = add(subType, inspectionState);
                }
                enclosingType = subType;
                dollar = nextDollar;
            }
            return typeInspection;
        }

        public TypeInspection.Builder ensureTypeInspection(TypeInfo typeInfo, InspectionState inspectionState) {
            assert Input.acceptFQN(typeInfo.packageName());
            synchronized (typeInspections) {
                TypeInspection.Builder inMap = typeInspections.get(typeInfo);
                if (inMap == null) {
                    TypeInspection.Builder typeInspection = new TypeInspectionImpl.Builder(typeInfo, inspectionState);
                    typeInspections.put(typeInfo, typeInspection);
                    return typeInspection;
                }
                return inMap;
            }
        }

        private TypeInfo extractPrimaryTypeAndAddToMap(Source source, int dollar) {
            String path = source.stripDotClass();
            String pathOfPrimaryType = dollar >= 0 ? path.substring(0, dollar) : path;
            String fqnOfPrimaryType = pathOfPrimaryType.replace('/', '.');
            TypeInfo primaryTypeInMap = get(fqnOfPrimaryType);
            if (primaryTypeInMap == null) {
                int lastDot = fqnOfPrimaryType.lastIndexOf('.');
                String packageName = fqnOfPrimaryType.substring(0, lastDot);
                String simpleName = fqnOfPrimaryType.substring(lastDot + 1);
                Identifier identifier = Identifier.from(source.uri());
                TypeInfo primaryType = new TypeInfo(identifier, packageName, simpleName);
                trie.add(primaryType.fullyQualifiedName.split("\\."), primaryType);
                return primaryType;
            }
            return primaryTypeInMap;
        }

        public TypeInfo getOrCreateFromPath(Source path, InspectionState inspectionState) {
            return getOrCreateFromPathReturnInspection(path, inspectionState).typeInfo();
        }

        public TypeInspection.Builder add(TypeInfo typeInfo, InspectionState inspectionState) {
            trie.add(typeInfo.fullyQualifiedName.split("\\."), typeInfo);
            synchronized (typeInspections) {
                TypeInspection.Builder inMap = typeInspections.get(typeInfo);
                if (inMap != null) {
                    throw new UnsupportedOperationException("Expected to know type inspection of "
                            + typeInfo.fullyQualifiedName);
                }
                assert !typeInfo.typeInspection.isSet() : "type " + typeInfo.fullyQualifiedName;
                TypeInspectionImpl.Builder ti = new TypeInspectionImpl.Builder(typeInfo, inspectionState);
                typeInspections.put(typeInfo, ti);
                return ti;
            }
        }

        public TypeInspection.Builder ensureTypeAndInspection(TypeInfo typeInfo, InspectionState inspectionState) {
            TypeInfo inMap = get(typeInfo.fullyQualifiedName);
            if (inMap == null) {
                return add(typeInfo, inspectionState);
            }
            return ensureTypeInspection(inMap, inspectionState);
        }

        public void registerFieldInspection(FieldInfo fieldInfo, FieldInspection.Builder builder) {
            if (fieldInspections.put(fieldInfo, builder) != null) {
                throw new IllegalArgumentException("Re-registering field " + fieldInfo.fullyQualifiedName());
            }
        }

        public void registerMethodInspection(MethodInspection.Builder builder) {
            if (methodInspections.put(builder.getDistinguishingName(), builder) != null) {
                throw new IllegalArgumentException("Re-registering method " + builder.getDistinguishingName());
            }
        }

        @Override
        public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
            return e2ImmuAnnotationExpressions;
        }

        @Override
        public boolean isPackagePrefix(PackagePrefix packagePrefix) {
            return trie.isStrictPrefix(packagePrefix.prefix());
        }

        @Override
        public void visit(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer) {
            trie.visit(prefix, consumer);
        }

        @Override
        public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
            return fieldInspections.get(fieldInfo);
        }

        public InspectionState getInspectionState(TypeInfo typeInfo) {
            if (typeInfo.typeInspection.isSet()) {
                return typeInfo.typeInspection.get().getInspectionState();
            }
            synchronized (typeInspections) {
                TypeInspection.Builder typeInspection = typeInspections.get(typeInfo);
                if (typeInspection == null) {
                    return null; // not registered
                }
                return typeInspection.getInspectionState();
            }
        }

        @Override
        public MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier,
                                                                   TypeInfo typeInfo,
                                                                   String methodName) {
            return new MethodInspectionImpl.Builder(identifier, typeInfo, methodName, MethodInfo.MethodType.METHOD);
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
            if (typeInfo.typeInspection.isSet()) {
                return typeInfo.typeInspection.get();
            }
            TypeInspection.Builder typeInspection;
            synchronized (typeInspections) {
                typeInspection = typeInspections.get(typeInfo);
            }
            if (typeInspection == null) {
                return null;
            }
            if (typeInspection.getInspectionState() == TRIGGER_BYTECODE_INSPECTION) {
                // inspection state will be set to START_BYTECODE_INSPECTION when the class visitor starts
                inspectWithByteCodeInspector(typeInfo);
                if (typeInspection.getInspectionState().lt(FINISHED_BYTECODE)) {
                    // trying to avoid cycles... we'll try again later
                    typeInspection.setInspectionState(TRIGGER_BYTECODE_INSPECTION);
                }
            } else if (typeInspection.getInspectionState() == TRIGGER_JAVA_PARSER) {
                try {
                    LOGGER.debug("Triggering Java parser on {}", typeInfo.fullyQualifiedName);
                    inspectWithJavaParser.inspect(typeInfo, typeInspection);
                } catch (ParseException e) {
                    String message = "Caught parse exception inspecting " + typeInfo.fullyQualifiedName;
                    throw new UnsupportedOperationException(message, e);
                }
                if (typeInspection.getInspectionState().lt(FINISHED_JAVA_PARSER)) {
                    throw new UnsupportedOperationException("? expected the java parser to do its job");
                }
            }
            // always not null here
            return typeInspection;
        }

        @Override
        public MethodInspection getMethodInspection(MethodInfo methodInfo) {
            String dn = methodInfo.distinguishingName();
            MethodInspection methodInspection = methodInspections.get(dn);
            if (methodInspection != null) return methodInspection;
            // see if we can trigger an inspection
            getTypeInspection(methodInfo.typeInfo);
            // try again
            MethodInspection take2 = methodInspections.get(dn);
            if (take2 == null) {
                throw new UnsupportedOperationException("No inspection for " + dn);
            }
            return take2;
        }

        public MethodInspection getMethodInspectionDoNotTrigger(String distinguishingName) {
            return methodInspections.get(distinguishingName);
        }

        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        public Stream<Map.Entry<TypeInfo, TypeInspection.Builder>> streamTypes() {
            synchronized (typeInspections) {
                return typeInspections.entrySet().stream();
            }
        }

        @Override
        public TypeInspection getTypeInspectionDoNotTrigger(TypeInfo typeInfo) {
            synchronized (typeInspections) {
                return typeInspections.get(typeInfo);
            }
        }

        @Override
        public TypeInspector newTypeInspector(TypeInfo typeInfo, boolean fullInspection, boolean dollarTypesAreNormalTypes) {
            return new TypeInspectorImpl(this, typeInfo, fullInspection, dollarTypesAreNormalTypes,
                    inspectWithJavaParser.storeComments());
        }

        // inspect from class path
        private void inspectWithByteCodeInspector(TypeInfo typeInfo) {
            Source source = byteCodeInspector.fqnToPath(typeInfo.fullyQualifiedName);
            if (source != null) {
                byteCodeInspector.inspectFromPath(source);
            } // else ignore
        }

        public void setByteCodeInspector(OnDemandInspection byteCodeInspector) {
            this.byteCodeInspector = byteCodeInspector;
        }

        public void setInspectWithJavaParser(InspectWithJavaParser inspectWithJavaParser) {
            this.inspectWithJavaParser = inspectWithJavaParser;
        }

        // we can probably do without this method; then the mutable versions will be used more
        public void makeParametersImmutable() {
            methodInspections.values().forEach(MethodInspection.Builder::makeParametersImmutable);
        }

        public TypeInfo syntheticFunction(int numberOfParameters, boolean isVoid) {
            String name = (isVoid ? Primitives.SYNTHETIC_CONSUMER : Primitives.SYNTHETIC_FUNCTION) + numberOfParameters;
            String fqn = "_internal_." + name;
            TypeInfo existing = get(fqn);
            if (existing != null) return existing;

            TypeInfo typeInfo = new TypeInfo(Identifier.INTERNAL_TYPE, Primitives.INTERNAL, name);
            TypeInspection.Builder builder = add(typeInfo, BY_HAND_WITHOUT_STATEMENTS);

            boolean isIndependent = isVoid && numberOfParameters == 0;
            if (isIndependent) {
                // this is the equivalent of interface Runnable { void run(); }
                builder.addAnnotation(e2ImmuAnnotationExpressions.independent);
            } else {
                AnnotationExpression independentHc = new AnnotationExpressionImpl(e2ImmuAnnotationExpressions.independent.typeInfo(),
                        List.of(new MemberValuePair(E2ImmuAnnotationExpressions.HIDDEN_CONTENT, new BooleanConstant(primitives, true))));
                builder.addAnnotation(independentHc);
            }
            boolean isContainer = typeInfo.simpleName.equals(Primitives.SYNTHETIC_FUNCTION_0);
            if (isContainer) {
                builder.addAnnotation(e2ImmuAnnotationExpressions.container);
            }

            builder.setParentClass(primitives.objectParameterizedType);
            builder.setTypeNature(TypeNature.INTERFACE);
            List<TypeParameter> tps = new ArrayList<>();
            for (int i = 0; i < numberOfParameters + (isVoid ? 0 : 1); i++) {
                TypeParameterImpl typeParameter = new TypeParameterImpl(typeInfo, "P" + i, i);
                typeParameter.setTypeBounds(List.of());
                builder.addTypeParameter(typeParameter);
                tps.add(typeParameter);
            }
            builder.setAccess(Inspection.Access.PUBLIC);
            builder.addAnnotation(primitives.functionalInterfaceAnnotationExpression);
            ParameterizedType returnType = isVoid ? primitives.voidParameterizedType :
                    new ParameterizedType(tps.get(numberOfParameters), 0, NONE);
            String methodName = methodNameOfFunctionalInterface(isVoid, numberOfParameters,
                    returnType.isBooleanOrBoxedBoolean());
            MethodInspection.Builder m = new MethodInspectionImpl.Builder(typeInfo, methodName, MethodInfo.MethodType.METHOD);
            m.addAnnotation(e2ImmuAnnotationExpressions.modified);
            m.setReturnType(returnType);
            for (int i = 0; i < numberOfParameters; i++) {
                m.addParameter(new ParameterInspectionImpl.Builder(Identifier.generate("param synthetic function"),
                        new ParameterizedType(tps.get(i), 0, NONE), "p" + i, i));
            }
            m.readyToComputeFQN(this);
            m.setAccess(Inspection.Access.PUBLIC);
            MethodInspection mi = m.build(this);
            registerMethodInspection(m);
            builder.addMethod(mi.getMethodInfo()).setFunctionalInterface(true);
            typeInfo.typeInspection.set(builder.build(null));
            return typeInfo;
        }

        private String methodNameOfFunctionalInterface(boolean isVoid, int numberOfParameters, boolean isPredicate) {
            if (isVoid) return "accept";
            if (numberOfParameters == 0) return "get";
            if (isPredicate) return "test";
            return "apply";
        }
    }
}
