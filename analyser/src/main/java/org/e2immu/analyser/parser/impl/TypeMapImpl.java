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
import java.util.concurrent.locks.ReentrantLock;
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

        private static class TypeData {
            final TypeInspection.Builder typeInspectionBuilder;
            final Map<String, MethodInspection.Builder> methodInspections = new HashMap<>();
            final Map<FieldInfo, FieldInspection.Builder> fieldInspections = new HashMap<>();
            InspectionState inspectionState;

            TypeData(TypeInspection.Builder typeInspectionBuilder, InspectionState inspectionState) {
                this.typeInspectionBuilder = typeInspectionBuilder;
                this.inspectionState = inspectionState;
            }

            @Override
            public boolean equals(Object o) {
                return o instanceof TypeData td && td.typeInspectionBuilder.typeInfo().equals(typeInspectionBuilder.typeInfo());
            }

            @Override
            public int hashCode() {
                return typeInspectionBuilder.typeInfo().fullyQualifiedName.hashCode();
            }
        }

        private final Trie<TypeInfo> trie;
        private final PrimitivesImpl primitives;
        private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;
        private final Resources classPath;

        private final Map<String, TypeData> typeInspections;

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
            return getOrCreateFromClassPathEnsureEnclosing(path, TRIGGER_BYTECODE_INSPECTION).typeInfo();
        }

        public TypeMapImpl build() {
            trie.freeze();

            typeInspections.values().forEach(typeData -> {
                TypeInfo typeInfo = typeData.typeInspectionBuilder.typeInfo();
                assert Input.acceptFQN(typeInfo.packageName());
                if (typeData.inspectionState.isDone() && !typeInfo.typeInspection.isSet()) {
                    typeInfo.typeInspection.set(typeData.typeInspectionBuilder.build(this));
                }
                typeData.methodInspections.values().forEach(methodInspectionBuilder -> {
                    MethodInfo methodInfo = methodInspectionBuilder.getMethodInfo();
                    if (!methodInfo.methodInspection.isSet() && methodInfo.typeInfo.typeInspection.isSet()) {
                        methodInspectionBuilder.build(this); // will set the inspection itself
                    }
                });
                typeData.fieldInspections.forEach((fieldInfo, fieldInspectionBuilder) -> {
                    if (!fieldInfo.fieldInspection.isSet() && fieldInfo.owner.typeInspection.isSet()) {
                        fieldInfo.fieldInspection.set(fieldInspectionBuilder.build(this));
                    }
                });
            });


            // we make a new map, because the resolver will encounter new types (which we will ignore)
            // all methods not yet resolved, will be resolved here.
            new HashSet<>(typeInspections.values()).forEach(typeData -> {
                TypeInfo typeInfo = typeData.typeInspectionBuilder.typeInfo();
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
                enclosingType = subType;
                dollar = nextDollar;
            }
            return typeInspection;
        }

        public TypeInspection.Builder ensureTypeInspection(TypeInfo typeInfo, InspectionState inspectionState) {
            assert Input.acceptFQN(typeInfo.packageName());
            TypeData typeData = typeInspections.get(typeInfo.fullyQualifiedName);
            if (typeData == null) {
                TypeInspection.Builder typeInspection = new TypeInspectionImpl.Builder(typeInfo,
                        inspectionState.getInspector());
                typeInspections.put(typeInfo.fullyQualifiedName, new TypeData(typeInspection, inspectionState));
                return typeInspection;
            }
            return typeData.typeInspectionBuilder;
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

        public TypeInspection.Builder add(TypeInfo typeInfo, InspectionState inspectionState) {
            trie.add(typeInfo.fullyQualifiedName.split("\\."), typeInfo);
            TypeData inMap = typeInspections.get(typeInfo.fullyQualifiedName);
            if (inMap != null) {
                throw new UnsupportedOperationException("Expected to know type inspection of "
                        + typeInfo.fullyQualifiedName);
            }
            assert !typeInfo.typeInspection.isSet() : "type " + typeInfo.fullyQualifiedName;
            TypeInspectionImpl.Builder ti = new TypeInspectionImpl.Builder(typeInfo, inspectionState.getInspector());
            typeInspections.put(typeInfo.fullyQualifiedName, new TypeData(ti, inspectionState));
            return ti;
        }

        private void add(TypeInspection.Builder typeInspection, InspectionState inspectionState) {
            String fqn = typeInspection.typeInfo().fullyQualifiedName;
            trie.add(fqn.split("\\."), typeInspection.typeInfo());
            TypeData inMap = typeInspections.get(fqn);
            if (inMap != null) {
                inMap.inspectionState = inspectionState;
            } else {
                TypeData typeData = new TypeData(typeInspection, inspectionState);
                typeInspections.put(fqn, typeData);
            }
        }

        // return type inspection, but null when still TRIGGER_BYTECODE
        @Override
        public TypeInspection getTypeInspectionToStartResolving(TypeInfo typeInfo) {
            TypeData typeData = typeInspections.get(typeInfo.fullyQualifiedName);
            if (typeData == null || typeData.inspectionState == TRIGGER_BYTECODE_INSPECTION) return null;
            return typeData.typeInspectionBuilder;
        }

        @Override
        public InspectionAndState typeInspectionSituation(String fqn) {
            TypeData typeData = typeInspections.get(fqn);
            if (typeData == null) return null; // not known
            return new InspectionAndState(typeData.typeInspectionBuilder, typeData.inspectionState);
        }

        public TypeInspection.Builder ensureTypeAndInspection(TypeInfo typeInfo, InspectionState inspectionState) {
            TypeInfo inMap = get(typeInfo.fullyQualifiedName);
            if (inMap == null) {
                return add(typeInfo, inspectionState);
            }
            return ensureTypeInspection(inMap, inspectionState);
        }

        public void registerFieldInspection(FieldInfo fieldInfo, FieldInspection.Builder builder) {
            TypeData typeData = typeInspections.get(fieldInfo.owner.fullyQualifiedName);
            if (typeData == null) {
                add(fieldInfo.owner, TRIGGER_BYTECODE_INSPECTION);
                typeData = typeInspections.get(fieldInfo.owner.fullyQualifiedName);
            }
            if (typeData.fieldInspections.put(fieldInfo, builder) != null) {
                throw new IllegalArgumentException("Re-registering field " + fieldInfo.fullyQualifiedName());
            }
        }

        public void registerMethodInspection(MethodInspection.Builder builder) {
            TypeInfo typeInfo = builder.methodInfo().typeInfo;
            TypeData typeData = typeInspections.get(typeInfo.fullyQualifiedName);
            if (typeData == null) {
                add(typeInfo, TRIGGER_BYTECODE_INSPECTION);
                typeData = typeInspections.get(typeInfo.fullyQualifiedName);
            }
            if (typeData.methodInspections.put(builder.getDistinguishingName(), builder) != null) {
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
            TypeData typeData = typeInspections.get(fieldInfo.owner.fullyQualifiedName);
            return typeData.fieldInspections.get(fieldInfo);
        }

        public InspectionState getInspectionState(TypeInfo typeInfo) {
            if (typeInfo.typeInspection.isSet()) {
                return BUILT;
            }
            TypeData typeData = typeInspections.get(typeInfo.fullyQualifiedName);
            if (typeData == null) {
                return null; // not registered
            }
            return typeData.inspectionState;
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
                List<InspectionAndState> typesInspected = byteCodeInspector.inspectFromPath(source);
                boolean found = false;
                for (InspectionAndState ias : typesInspected) {
                    if (ias.typeInspection().typeInfo().equals(typeInfo)) found = true;
                    String fullyQualifiedName = ias.typeInspection().typeInfo().fullyQualifiedName;
                    trie.add(fullyQualifiedName.split("\\."), ias.typeInspection().typeInfo());
                    if (ias.typeInspection() instanceof TypeInspection.Builder builder) {
                        TypeData typeData = typeInspections.get(fullyQualifiedName);
                        if (typeData == null) {
                            typeInspections.put(fullyQualifiedName, new TypeData(builder, ias.state()));
                        }
                    } else {
                        assert ias.state().isDone();
                        // no point
                    }
                }
                return found;
            } // else ignore
            return false;
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
            if (typeInfo.typeInspection.isSet()) {
                return typeInfo.typeInspection.get();
            }
            TypeData typeData = typeInspections.get(typeInfo.fullyQualifiedName);
            if (typeData == null) {
                return null;
            }
            TypeInspection.Builder typeInspection = typeData.typeInspectionBuilder;
            if (typeData.inspectionState == TRIGGER_BYTECODE_INSPECTION) {
                typeData.inspectionState = STARTING_BYTECODE;
                boolean success = inspectWithByteCodeInspector(typeInfo);
                // we may have to try later, because of cyclic dependencies
                typeData.inspectionState = success ? FINISHED_BYTECODE : TRIGGER_BYTECODE_INSPECTION;
            } else if (typeData.inspectionState == TRIGGER_JAVA_PARSER) {
                try {
                    LOGGER.debug("Triggering Java parser on {}", typeInfo.fullyQualifiedName);
                    typeData.inspectionState = STARTING_JAVA_PARSER;
                    inspectWithJavaParser.inspect(typeInspection);
                    typeData.inspectionState = FINISHED_JAVA_PARSER;
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
            MethodInspection methodInspection = typeData2.methodInspections.get(dn);
            if (methodInspection != null) return methodInspection;
            throw new UnsupportedOperationException("No inspection for " + dn);
        }

        public MethodInspection getMethodInspectionDoNotTrigger(TypeInfo typeInfo, String distinguishingName) {
            TypeData typeData = typeInspections.get(typeInfo.fullyQualifiedName);
            return typeData.methodInspections.get(distinguishingName);
        }

        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        public Stream<TypeInfo> streamTypesStartingByteCode() {
            return typeInspections.values().stream()
                    .filter(typeData -> typeData.inspectionState == STARTING_BYTECODE)
                    .map(typeData -> typeData.typeInspectionBuilder.typeInfo());
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
            typeInspections.values().forEach(td ->
                    td.methodInspections.values().forEach(MethodInspection.Builder::makeParametersImmutable));
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
