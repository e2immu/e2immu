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
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.bytecode.TypeData;
import org.e2immu.analyser.bytecode.TypeDataImpl;
import org.e2immu.analyser.inspector.InspectionState;
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
import java.util.concurrent.locks.ReentrantReadWriteLock;
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

    private static TypeInfo get(Trie<TypeInfo> trie, String fullyQualifiedName) {
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
        private final ReentrantReadWriteLock trieLock = new ReentrantReadWriteLock();
        private final ReentrantReadWriteLock.ReadLock trieReadLock = trieLock.readLock();

        private final PrimitivesImpl primitives;
        private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
        private final Resources classPath;

        private final Map<String, TypeData> typeInspections;
        private final ReentrantReadWriteLock tiLock = new ReentrantReadWriteLock();
        private final ReentrantReadWriteLock.ReadLock tiReadLock = tiLock.readLock();

        private ByteCodeInspector byteCodeInspector;
        private InspectWithJavaParser inspectWithJavaParser;

        public Builder(Resources resources) {
            trie = new Trie<>();
            primitives = new PrimitivesImpl();
            classPath = resources;
            e2ImmuAnnotationExpressions = new E2ImmuAnnotationExpressions();
            typeInspections = new HashMap<>();

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
        }

        public TypeMapImpl build() {
            trieLock.writeLock().lock();
            try {
                trie.freeze();
            } finally {
                trieLock.writeLock().unlock();
            }
            tiLock.writeLock().lock();
            try {
                typeInspections.values().forEach(typeData -> {
                    String fqn = typeData.getTypeInspectionBuilder().typeInfo().fullyQualifiedName;
                    TypeInfo typeInfo = trie.get(fqn.split("\\.")).get(0);
                    assert Input.acceptFQN(typeInfo.packageName());
                    if (typeData.getInspectionState().isDone() && !typeInfo.typeInspection.isSet()) {
                        typeInfo.typeInspection.set(typeData.getTypeInspectionBuilder().build(this));
                        assert typeInfo == typeInfo.typeInspection.get().typeInfo();
                    }
                    typeData.methodInspectionBuilders().forEach(e -> {
                        MethodInfo methodInfo = e.getValue().getMethodInfo();
                        if (!methodInfo.methodInspection.isSet() && methodInfo.typeInfo.typeInspection.isSet()) {
                            e.getValue().build(this); // will set the inspection itself
                        }
                    });
                    typeData.fieldInspectionBuilders().forEach(e -> {
                        FieldInfo fieldInfo = e.getKey();
                        if (!fieldInfo.fieldInspection.isSet() && fieldInfo.owner.typeInspection.isSet()) {
                            fieldInfo.fieldInspection.set(e.getValue().build(this));
                        }
                    });
                });


                // we make a new map, because the resolver will encounter new types (which we will ignore)
                // all methods not yet resolved, will be resolved here.
                new HashSet<>(typeInspections.values()).forEach(typeData -> {
                    TypeInfo typeInfo = typeData.getTypeInspectionBuilder().typeInfo();
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
            } finally {
                tiLock.writeLock().unlock();
            }
        }

        @Override
        public TypeInfo get(Class<?> clazz) {
            return get(clazz.getCanonicalName());
        }

        @Override
        public TypeInfo get(String fullyQualifiedName) {
            trieReadLock.lock();
            try {
                return TypeMapImpl.get(trie, fullyQualifiedName);
            } finally {
                trieReadLock.unlock();
            }
        }

        @Override
        public TypeInfo getOrCreateByteCode(String packageName, String simpleName) {
            Source source = classPath.fqnToPath(packageName + "." + simpleName, ".class");
            if (source == null) return null;
            // example of not-accessible type: java.lang.Compiler
            Identifier id = Identifier.from(source.uri());
            return getOrCreate(packageName, simpleName, id, TRIGGER_BYTECODE_INSPECTION).typeInfo();
        }

        @Override
        public TypeInfo getOrCreate(String fqn, boolean complain) {
            int lastDot = fqn.lastIndexOf('.');
            String packageName = lastDot < 0 ? "" : fqn.substring(0, lastDot);
            String simpleName = lastDot < 0 ? fqn : fqn.substring(lastDot + 1);
            TypeInfo typeInfo = getOrCreateByteCode(packageName, simpleName);
            if (typeInfo == null) {
                if (complain) throw new UnsupportedOperationException("Cannot find " + fqn);
                return null;
            }
            return typeInfo;
        }

        /*
         Entry point of InspectAll, with state INIT_JAVA.
         If in trie, but not yet in typeInspections, it will be added to both.
         */
        @Override
        public TypeInspection.Builder getOrCreate(String packageName,
                                                  String simpleName,
                                                  Identifier identifier,
                                                  InspectionState inspectionState) {
            assert simpleName.indexOf('.') < 0; // no dots!
            TypeInfo typeInfo = get(packageName + "." + simpleName);
            if (typeInfo != null) {
                return ensureTypeInspection(typeInfo, inspectionState);
            }
            TypeInfo newType = new TypeInfo(identifier, packageName, simpleName);
            return add(newType, inspectionState);
        }

        /*
        Creates types all the way up to the primary type if necessary
         */
        @Override
        public TypeInspection.Builder getOrCreateFromClassPathEnsureEnclosing(Source source,
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
                // rather than subType, because 'add' can replace the typeInfo object
                enclosingType = typeInspection.typeInfo();
                dollar = nextDollar;
            }
            return typeInspection;
        }

        public TypeInspection.Builder ensureTypeInspection(TypeInfo typeInfo, InspectionState inspectionState) {
            assert Input.acceptFQN(typeInfo.packageName());
            tiReadLock.lock();
            try {
                TypeData typeData = typeInspections.get(typeInfo.fullyQualifiedName);
                if (typeData != null) {
                    return typeData.getTypeInspectionBuilder();
                }
            } finally {
                tiReadLock.unlock();
            }
            tiLock.writeLock().lock();
            try {
                // check again, we could have been in a queue with multiple waiting to write this
                TypeData typeData2 = typeInspections.get(typeInfo.fullyQualifiedName);
                if (typeData2 != null) {
                    return typeData2.getTypeInspectionBuilder();
                }
                TypeInspection.Builder typeInspection = new TypeInspectionImpl.Builder(typeInfo,
                        inspectionState.getInspector());
                typeInspections.put(typeInfo.fullyQualifiedName, new TypeDataImpl(typeInspection, inspectionState));
                return typeInspection;
            } finally {
                tiLock.writeLock().unlock();
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
                return addToTrie(primaryType);
            }
            return primaryTypeInMap;
        }

        @Override
        public TypeInfo addToTrie(TypeInfo typeInfo) {
            trieLock.writeLock().lock();
            try {
                return trie.addIfNodeDataEmpty(typeInfo.fullyQualifiedName.split("\\."), typeInfo);
            } finally {
                trieLock.writeLock().unlock();
            }
        }

        public TypeInspection.Builder add(TypeInfo typeInfoIn, InspectionState inspectionState) {
            TypeInfo typeInfo = addToTrie(typeInfoIn);
            tiReadLock.lock();
            try {
                TypeData inMap = typeInspections.get(typeInfo.fullyQualifiedName);
                if (inMap != null) {
                    return inMap.getTypeInspectionBuilder();
                }
            } finally {
                tiReadLock.unlock();
            }
            tiLock.writeLock().lock();
            try {
                // check again, we could have been in a queue
                TypeData inMap = typeInspections.get(typeInfo.fullyQualifiedName);
                if (inMap != null) {
                    return inMap.getTypeInspectionBuilder();
                }
                assert !typeInfo.typeInspection.isSet() : "type " + typeInfo.fullyQualifiedName;
                TypeInspectionImpl.Builder ti = new TypeInspectionImpl.Builder(typeInfo, inspectionState.getInspector());
                typeInspections.put(typeInfo.fullyQualifiedName, new TypeDataImpl(ti, inspectionState));
                return ti;
            } finally {
                tiLock.writeLock().unlock();
            }
        }

        // return type inspection, but null when still TRIGGER_BYTECODE
        @Override
        public TypeInspection getTypeInspectionToStartResolving(TypeInfo typeInfo) {
            tiReadLock.lock();
            try {
                TypeData typeData = typeInspections.get(typeInfo.fullyQualifiedName);
                if (typeData == null || typeData.getInspectionState() == TRIGGER_BYTECODE_INSPECTION) return null;
                return typeData.getTypeInspectionBuilder();
            } finally {
                tiReadLock.unlock();
            }
        }

        @Override
        public InspectionAndState typeInspectionSituation(String fqn) {
            tiReadLock.lock();
            try {
                TypeData typeData = typeInspections.get(fqn);
                if (typeData == null) return null; // not known
                return new InspectionAndState(typeData.getTypeInspectionBuilder(), typeData.getInspectionState());
            } finally {
                tiReadLock.unlock();
            }
        }

        public void registerFieldInspection(FieldInfo fieldInfo, FieldInspection.Builder builder) {
            trieReadLock.lock();
            try {
                TypeData typeData = typeInspections.get(fieldInfo.owner.fullyQualifiedName);
                if (typeData == null) {
                    TypeInfo typeInfo = add(fieldInfo.owner, TRIGGER_BYTECODE_INSPECTION).typeInfo();
                    assert typeInfo == fieldInfo.owner; // we're in a read-lock on the trie
                    typeData = typeInspections.get(fieldInfo.owner.fullyQualifiedName);
                }
                if (typeData.fieldInspectionsPut(fieldInfo, builder) != null) {
                    throw new IllegalArgumentException("Re-registering field " + fieldInfo.fullyQualifiedName());
                }
            } finally {
                trieReadLock.unlock();
            }
        }

        public void registerMethodInspection(MethodInspection.Builder builder) {
            TypeInfo typeInfo = builder.methodInfo().typeInfo;
            trieReadLock.lock();
            try {
                TypeData typeData = typeInspections.get(typeInfo.fullyQualifiedName);
                if (typeData == null) {
                    TypeInfo ti = add(typeInfo, TRIGGER_BYTECODE_INSPECTION).typeInfo();
                    assert ti == typeInfo; // we're in a read-lock on the trie
                    typeData = typeInspections.get(typeInfo.fullyQualifiedName);
                }
                if (typeData.methodInspectionsPut(builder.getDistinguishingName(), builder) != null) {
                    throw new IllegalArgumentException("Re-registering method " + builder.getDistinguishingName());
                }
            } finally {
                trieReadLock.unlock();
            }
        }

        @Override
        public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
            return e2ImmuAnnotationExpressions;
        }

        @Override
        public boolean isPackagePrefix(PackagePrefix packagePrefix) {
            trieReadLock.lock();
            try {
                return trie.isStrictPrefix(packagePrefix.prefix());
            } finally {
                trieReadLock.unlock();
            }
        }

        @Override
        public void visit(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer) {
            trieReadLock.lock();
            try {
                trie.visit(prefix, consumer);
            } finally {
                trieReadLock.unlock();
            }
        }

        @Override
        public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
            tiReadLock.lock();
            try {
                TypeData typeData = typeInspections.get(fieldInfo.owner.fullyQualifiedName);
                return typeData.fieldInspectionsGet(fieldInfo);
            } finally {
                tiReadLock.unlock();
            }
        }

        public InspectionState getInspectionState(TypeInfo typeInfo) {
            if (typeInfo.typeInspection.isSet()) {
                return BUILT;
            }
            tiReadLock.lock();
            try {
                TypeData typeData = typeInspections.get(typeInfo.fullyQualifiedName);
                if (typeData == null) {
                    return null; // not registered
                }
                return typeData.getInspectionState();
            } finally {
                tiReadLock.unlock();
            }
        }

        @Override
        public MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier,
                                                                   TypeInfo typeInfo,
                                                                   String methodName) {
            return new MethodInspectionImpl.Builder(identifier, typeInfo, methodName, MethodInfo.MethodType.METHOD);
        }


        // return false in case of cyclic dependencies
        private boolean inspectWithByteCodeInspector(TypeInfo typeInfo) {
            Source source = classPath.fqnToPath(typeInfo.fullyQualifiedName, ".class");
            if (source != null) {
                List<TypeData> data = byteCodeInspector.inspectFromPath(source);
                copyIntoTypeMap(data);
                TypeData ti = typeInspections.get(typeInfo.fullyQualifiedName);
                return ti.getInspectionState().isDone();
            } // else ignore
            return false;
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
            if (typeInfo.typeInspection.isSet()) {
                return typeInfo.typeInspection.get();
            }
            TypeData typeData;
            tiReadLock.lock();
            TypeInspection.Builder typeInspection;
            try {
                typeData = typeInspections.get(typeInfo.fullyQualifiedName);
                if (typeData == null) {
                    return null;
                }
                typeInspection = typeData.getTypeInspectionBuilder();
                if (typeData.getInspectionState().isDone()) {
                    return typeInspection;
                }
            } finally {
                tiReadLock.unlock();
            }
            // here we could arrive two threads at the same time; we'll not block that
            if (Inspector.BYTE_CODE_INSPECTION.equals(typeData.getInspectionState().getInspector())) {
                typeData.setInspectionState(STARTING_BYTECODE);
                boolean success = inspectWithByteCodeInspector(typeInfo);
                if (success) {
                    // we'll have overwritten the typeData
                    return typeInspections.get(typeInfo.fullyQualifiedName).getTypeInspectionBuilder();
                }
                // we may have to try later, because of cyclic dependencies
            } else if (typeData.getInspectionState() == TRIGGER_JAVA_PARSER || typeData.getInspectionState() == INIT_JAVA_PARSER) {
                try {
                    typeData.setInspectionState(STARTING_JAVA_PARSER);
                    LOGGER.debug("Triggering Java parser on {}", typeInfo.fullyQualifiedName);
                    inspectWithJavaParser.inspect(typeInspection);
                    typeData.setInspectionState(FINISHED_JAVA_PARSER);
                } catch (ParseException e) {
                    String message = "Caught parse exception inspecting " + typeInfo.fullyQualifiedName;
                    throw new UnsupportedOperationException(message, e);
                }
            }
            // always not null here
            return typeInspection;
        }

        @Override
        public MethodInspection getMethodInspection(MethodInfo methodInfo) {
            tiReadLock.lock();
            try {
                TypeData typeData1 = typeInspections.get(methodInfo.typeInfo.fullyQualifiedName);
                TypeData typeData2;
                if (typeData1 == null) {
                    // see if we can trigger an inspection
                    getTypeInspection(methodInfo.typeInfo);
                    typeData2 = typeInspections.get(methodInfo.typeInfo.fullyQualifiedName);
                } else {
                    typeData2 = typeData1;
                }
                String dn = methodInfo.distinguishingName();
                MethodInspection methodInspection = typeData2.methodInspectionsGet(dn);
                if (methodInspection != null) return methodInspection;
                throw new UnsupportedOperationException("No inspection for " + dn);
            } finally {
                tiReadLock.unlock();
            }
        }

        public MethodInspection getMethodInspectionDoNotTrigger(TypeInfo typeInfo, String distinguishingName) {
            tiReadLock.lock();
            try {
                TypeData typeData = typeInspections.get(typeInfo.fullyQualifiedName);
                if (!typeData.getInspectionState().isDone()) {
                    getTypeInspection(typeInfo);
                }
                return typeData.methodInspectionsGet(distinguishingName);
            } finally {
                tiReadLock.unlock();
            }
        }

        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        public Stream<TypeInfo> streamTypesStartingByteCode() {
            tiReadLock.lock();
            try {
                return typeInspections.values().stream()
                        .filter(typeData -> typeData.getInspectionState() == STARTING_BYTECODE)
                        .map(typeData -> typeData.getTypeInspectionBuilder().typeInfo());
            } finally {
                tiReadLock.unlock();
            }
        }

        @Override
        public TypeInspector newTypeInspector(TypeInfo typeInfo, boolean fullInspection, boolean dollarTypesAreNormalTypes) {
            TypeInspection.Builder typeInspection = add(typeInfo, InspectionState.STARTING_JAVA_PARSER);
            return new TypeInspectorImpl(typeInspection, fullInspection, dollarTypesAreNormalTypes,
                    inspectWithJavaParser.storeComments());
        }

        public void setByteCodeInspector(ByteCodeInspector byteCodeInspector) {
            this.byteCodeInspector = byteCodeInspector;
        }

        public void setInspectWithJavaParser(InspectWithJavaParser inspectWithJavaParser) {
            this.inspectWithJavaParser = inspectWithJavaParser;
        }

        // we can probably do without this method; then the mutable versions will be used more
        public void makeParametersImmutable() {
            tiLock.writeLock().lock();
            try {
                typeInspections.values().forEach(td ->
                        td.methodInspectionBuilders().forEach(e -> e.getValue().makeParametersImmutable()));
            } finally {
                tiLock.writeLock().unlock();
            }
        }

        public TypeInfo syntheticFunction(int numberOfParameters, boolean isVoid) {
            String name = (isVoid ? Primitives.SYNTHETIC_CONSUMER : Primitives.SYNTHETIC_FUNCTION) + numberOfParameters;
            String fqn = "_internal_." + name;
            TypeInfo existing = get(fqn);
            if (existing != null) return existing;

            TypeInfo typeInfoOrig = new TypeInfo(Identifier.INTERNAL_TYPE, Primitives.INTERNAL, name);
            TypeInspection.Builder builder = add(typeInfoOrig, BY_HAND_WITHOUT_STATEMENTS);
            TypeInfo typeInfo = builder.typeInfo(); // in case there were two calls at the same time
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
            builder.addMethod(mi.getMethodInfo()).setFunctionalInterface(mi);
            typeInfo.typeInspection.set(builder.build(null));
            return typeInfo;
        }

        private String methodNameOfFunctionalInterface(boolean isVoid, int numberOfParameters, boolean isPredicate) {
            if (isVoid) return "accept";
            if (numberOfParameters == 0) return "get";
            if (isPredicate) return "test";
            return "apply";
        }

        // byte code inspection only!!!
        @Override
        public void copyIntoTypeMap(List<TypeData> data) {
            trieLock.writeLock().lock();
            try {
                for (TypeData typeData : data) {
                    TypeInfo typeInfo = typeData.getTypeInspectionBuilder().typeInfo();
                    trie.addIfNodeDataEmpty(typeInfo.fullyQualifiedName.split("\\."), typeInfo);
                }
            } finally {
                trieLock.writeLock().unlock();
            }
            tiLock.writeLock().lock();
            try {
                // overwrite, unless done
                for (TypeData ias : data) {
                    String fullyQualifiedName = ias.getTypeInspectionBuilder().typeInfo().fullyQualifiedName;
                    TypeData inMap = typeInspections.get(fullyQualifiedName);
                    if (inMap == null || !inMap.getInspectionState().isDone() && ias.getInspectionState().isDone()) {
                        LOGGER.debug("Writing type inspection of {}", fullyQualifiedName);
                        typeInspections.put(fullyQualifiedName, ias);
                    } else {
                        // overhead; is pretty low
                        LOGGER.debug("Not writing inspection of {}, state {}", fullyQualifiedName, ias.getInspectionState());
                    }
                }
            } finally {
                tiLock.writeLock().unlock();
            }
        }
    }
}
