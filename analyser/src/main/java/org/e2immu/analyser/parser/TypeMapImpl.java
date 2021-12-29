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

package org.e2immu.analyser.parser;

import com.github.javaparser.ParseException;
import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.inspector.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.resolver.Resolver;
import org.e2immu.analyser.resolver.ShallowMethodResolver;
import org.e2immu.analyser.util.Resources;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.analyser.util.Trie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState;
import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.*;
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

    public static boolean containsPrefix(Trie<TypeInfo> trie, String fullyQualifiedName) {
        String[] split = fullyQualifiedName.split("\\.");
        // we believe it is going to be a lot faster if we go from 1 to max length rather than the other way round
        // (there'll be more hits outside the source than inside the source dir)
        for (int i = 1; i <= split.length; i++) {
            List<TypeInfo> typeInfoList = trie.get(split, i);
            if (typeInfoList == null) return false;
            if (!typeInfoList.isEmpty()) return true;
        }
        return false;
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

    public static class Builder implements TypeMap {

        private final Trie<TypeInfo> trie = new Trie<>();
        private final Primitives primitives = new Primitives();
        private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions = new E2ImmuAnnotationExpressions();
        private final Resources classPath;

        private final Map<TypeInfo, TypeInspectionImpl.Builder> typeInspections = new HashMap<>();
        private final Map<FieldInfo, FieldInspectionImpl.Builder> fieldInspections = new HashMap<>();
        private final Map<String, MethodInspectionImpl.Builder> methodInspections = new HashMap<>();

        private ByteCodeInspector byteCodeInspector;
        private InspectWithJavaParser inspectWithJavaParser;

        public Builder(Resources classPath) {
            this.classPath = classPath;
            for (TypeInfo typeInfo : getPrimitives().typeByName.values()) {
                if (!typeInfo.typeInspection.isSet())
                    add(typeInfo, TRIGGER_BYTECODE_INSPECTION);
            }
            for (TypeInfo typeInfo : getPrimitives().primitiveByName.values()) {
                if (!typeInfo.typeInspection.isSet())
                    add(typeInfo, BY_HAND_WITHOUT_STATEMENTS);
            }
            e2ImmuAnnotationExpressions.streamTypes().forEach(typeInfo -> add(typeInfo, TRIGGER_BYTECODE_INSPECTION));
        }

        public TypeInfo loadType(String fqn, boolean complain) {
            TypeInfo inMap = get(fqn);
            if (inMap != null) return inMap;
            // we don't know it... so we don't know the boundary between primary and sub-type
            // we can either search in the class path, or in the source path

            String path = classPath.fqnToPath(fqn, ".class");
            if (path == null) {
                if (complain) {
                    LOGGER.error("ERROR: Cannot find type '{}'", fqn);
                    throw new NotFoundInClassPathException(fqn);
                }
                return null;
            }
            return getOrCreateFromPath(StringUtil.stripDotClass(path), TRIGGER_BYTECODE_INSPECTION);
        }

        public TypeMapImpl build() {
            trie.freeze();

            typeInspections.forEach((typeInfo, typeInspectionBuilder) -> {
                if (typeInspectionBuilder.finishedInspection() && !typeInfo.typeInspection.isSet()) {
                    typeInfo.typeInspection.set(typeInspectionBuilder.build());
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
                    fieldInfo.fieldInspection.set(fieldInspectionBuilder.build());
                }
            });

            // we make a new map, because the resolver will encounter new types (which we will ignore)
            // all methods not yet resolved, will be resolved here.
            new HashSet<>(typeInspections.keySet()).forEach(typeInfo -> {
                if (typeInfo.typeInspection.isSet()) {
                    if (!typeInfo.typeResolution.isSet()) {
                        Set<TypeInfo> superTypes = Resolver.superTypesExcludingJavaLangObject(InspectionProvider.DEFAULT, typeInfo, null);
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

        public TypeInfo getOrCreate(String packageName, String simpleName, InspectionState inspectionState) {
            assert simpleName.indexOf('.') < 0; // no dots!
            TypeInfo typeInfo = get(packageName + "." + simpleName);
            if (typeInfo != null) {
                ensureTypeInspection(typeInfo, inspectionState);
                return typeInfo;
            }
            TypeInfo newType = new TypeInfo(packageName, simpleName);
            add(newType, inspectionState);
            return newType;
        }
        /*
        Creates types all the way up to the primary type if necessary
         */

        public TypeInspectionImpl.Builder getOrCreateFromPathReturnInspection(String path, InspectionState inspectionState) {
            assert path.indexOf('.') < 0 : "Path is " + path; // no dots! uses / and $; the . is for the .class which should have been stripped
            int dollar = path.indexOf('$');
            TypeInfo primaryType = extractPrimaryTypeAndAddToMap(path, dollar);
            if (dollar < 0) return ensureTypeInspection(primaryType, inspectionState);
            TypeInfo enclosingType = primaryType;
            TypeInspectionImpl.Builder typeInspection = null;
            while (dollar >= 0) {
                int nextDollar = path.indexOf('$', dollar + 1);
                String simpleName = nextDollar < 0 ? path.substring(dollar + 1) : path.substring(dollar + 1, nextDollar);
                String fqn = enclosingType.fullyQualifiedName + "." + simpleName;
                TypeInfo subTypeInMap = get(fqn);
                TypeInfo subType;
                if (subTypeInMap != null) {
                    subType = subTypeInMap;
                    typeInspection = ensureTypeInspection(subType, inspectionState);
                } else {
                    subType = new TypeInfo(enclosingType, simpleName);
                    typeInspection = add(subType, inspectionState);
                }
                enclosingType = subType;
                dollar = nextDollar;
            }
            return typeInspection;
        }

        public TypeInspectionImpl.Builder ensureTypeInspection(TypeInfo typeInfo, InspectionState inspectionState) {
            TypeInspectionImpl.Builder inMap = typeInspections.get(typeInfo);
            if (inMap == null) {
                TypeInspectionImpl.Builder typeInspection = new TypeInspectionImpl.Builder(typeInfo, inspectionState);
                typeInspections.put(typeInfo, typeInspection);
                return typeInspection;
            }
            return inMap;
        }

        private TypeInfo extractPrimaryTypeAndAddToMap(String path, int dollar) {
            String pathOfPrimaryType = dollar >= 0 ? path.substring(0, dollar) : path;
            String fqnOfPrimaryType = pathOfPrimaryType.replace('/', '.');
            TypeInfo primaryTypeInMap = get(fqnOfPrimaryType);
            if (primaryTypeInMap == null) {
                int lastDot = fqnOfPrimaryType.lastIndexOf('.');
                String packageName = fqnOfPrimaryType.substring(0, lastDot);
                String simpleName = fqnOfPrimaryType.substring(lastDot + 1);
                TypeInfo primaryType = new TypeInfo(packageName, simpleName);
                trie.add(primaryType.fullyQualifiedName.split("\\."), primaryType);
                return primaryType;
            }
            return primaryTypeInMap;
        }

        public TypeInfo getOrCreateFromPath(String path, InspectionState inspectionState) {
            return getOrCreateFromPathReturnInspection(path, inspectionState).typeInfo();
        }

        public TypeInspectionImpl.Builder add(TypeInfo typeInfo, InspectionState inspectionState) {
            trie.add(typeInfo.fullyQualifiedName.split("\\."), typeInfo);
            TypeInspectionImpl.Builder inMap = typeInspections.get(typeInfo);
            if (inMap != null) {
                throw new UnsupportedOperationException();
            }
            assert !typeInfo.typeInspection.isSet() : "type " + typeInfo.fullyQualifiedName;
            TypeInspectionImpl.Builder ti = new TypeInspectionImpl.Builder(typeInfo, inspectionState);
            typeInspections.put(typeInfo, ti);
            return ti;
        }

        public TypeInspectionImpl.Builder ensureTypeAndInspection(TypeInfo typeInfo, InspectionState inspectionState) {
            TypeInfo inMap = get(typeInfo.fullyQualifiedName);
            if (inMap == null) {
                return add(typeInfo, inspectionState);
            }
            return ensureTypeInspection(inMap, inspectionState);
        }

        public void registerFieldInspection(FieldInfo fieldInfo, FieldInspectionImpl.Builder builder) {
            if (fieldInspections.put(fieldInfo, builder) != null) {
                throw new IllegalArgumentException("Re-registering field " + fieldInfo.fullyQualifiedName());
            }
        }

        public void registerMethodInspection(MethodInspectionImpl.Builder builder) {
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
            if (typeInfo.typeInspection.isSet()) return typeInfo.typeInspection.get().getInspectionState();
            TypeInspectionImpl.Builder typeInspection = typeInspections.get(typeInfo);
            if (typeInspection == null) {
                return null; // not registered
            }
            return typeInspection.getInspectionState();
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
            if (typeInfo.typeInspection.isSet()) {
                // primitives, etc.
                return typeInfo.typeInspection.get();
            }
            TypeInspectionImpl.Builder typeInspection = typeInspections.get(typeInfo);
            if (typeInspection == null) {
                return null;
            }
            if (typeInspection.getInspectionState() == TRIGGER_BYTECODE_INSPECTION) {
                typeInspection.setInspectionState(STARTING_BYTECODE);
                inspectWithByteCodeInspector(typeInfo);
                if (typeInspection.getInspectionState().lt(FINISHED_BYTECODE)) {
                    // trying to avoid cycles... we'll try again later
                    typeInspection.setInspectionState(TRIGGER_BYTECODE_INSPECTION);
                }
            } else if (typeInspection.getInspectionState() == TRIGGER_JAVA_PARSER) {
                try {
                    inspectWithJavaParser.inspect(typeInfo, typeInspection);
                } catch (ParseException e) {
                    throw new UnsupportedOperationException("Caught parse exception: " + e);
                }
                if (typeInspection.getInspectionState().lt(FINISHED_JAVA_PARSER)) {
                    throw new UnsupportedOperationException("? expected the java parser to do its job");
                }
            }
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

        public Stream<Map.Entry<TypeInfo, TypeInspectionImpl.Builder>> streamTypes() {
            return typeInspections.entrySet().stream();
        }


        // inspect from class path
        private void inspectWithByteCodeInspector(TypeInfo typeInfo) {
            String pathInClassPath = byteCodeInspector.getClassPath()
                    .fqnToPath(typeInfo.fullyQualifiedName, ".class");
            if (pathInClassPath != null) {
                byteCodeInspector.inspectFromPath(pathInClassPath);
            } // else ignore
        }

        public void setByteCodeInspector(ByteCodeInspector byteCodeInspector) {
            this.byteCodeInspector = byteCodeInspector;
        }

        public void setInspectWithJavaParser(InspectWithJavaParser inspectWithJavaParser) {
            this.inspectWithJavaParser = inspectWithJavaParser;
        }

        // we can probably do without this method; then the mutable versions will be used more
        public void makeParametersImmutable() {
            methodInspections.values().forEach(MethodInspectionImpl.Builder::makeParametersImmutable);
        }

        public TypeInfo syntheticFunction(int numberOfParameters, boolean isVoid) {
            String name = (isVoid ? "SyntheticConsumer" : "SyntheticFunction") + numberOfParameters;
            String fqn = "_internal_." + name;
            TypeInfo existing = get(fqn);
            if (existing != null) return existing;

            TypeInfo typeInfo = new TypeInfo("_internal_", name);
            TypeInspectionImpl.Builder builder = add(typeInfo, BY_HAND_WITHOUT_STATEMENTS);

            builder.setParentClass(primitives.objectParameterizedType);
            builder.setTypeNature(TypeNature.INTERFACE);
            List<TypeParameter> tps = new ArrayList<>();
            for (int i = 0; i < numberOfParameters + (isVoid ? 0 : 1); i++) {
                TypeParameterImpl typeParameter = new TypeParameterImpl(typeInfo, "P" + i, i);
                typeParameter.setTypeBounds(List.of());
                builder.addTypeParameter(typeParameter);
                tps.add(typeParameter);
            }
            builder.addTypeModifier(TypeModifier.PUBLIC);
            builder.addAnnotation(primitives.functionalInterfaceAnnotationExpression);

            MethodInspectionImpl.Builder m = new MethodInspectionImpl.Builder(typeInfo, isVoid ? "accept" : "apply");
            m.setReturnType(isVoid ? primitives.voidParameterizedType :
                    new ParameterizedType(tps.get(numberOfParameters), 0, NONE));
            for (int i = 0; i < numberOfParameters; i++) {
                m.addParameter(new ParameterInspectionImpl.Builder(Identifier.generate(),
                        new ParameterizedType(tps.get(i), 0, NONE), "p" + i, i));
            }
            m.readyToComputeFQN(this);
            m.addModifier(MethodModifier.PUBLIC);
            MethodInspection mi = m.build(this);
            registerMethodInspection(m);
            builder.addMethod(mi.getMethodInfo()).setFunctionalInterface(true);
            typeInfo.typeInspection.set(builder.build());
            return typeInfo;
        }
    }
}
