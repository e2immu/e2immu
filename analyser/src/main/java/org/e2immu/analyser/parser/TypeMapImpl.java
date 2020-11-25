/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.parser;

import org.e2immu.analyser.bytecode.ByteCodeInspector;
import org.e2immu.analyser.inspector.FieldInspectionImpl;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.Trie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState;
import static org.e2immu.analyser.inspector.TypeInspectionImpl.InspectionState.*;

public class TypeMapImpl implements TypeMap {

    private final Trie<TypeInfo> trie;
    private final Primitives primitives;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;

    private TypeMapImpl(Trie<TypeInfo> trie, Primitives primitives, E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {
        this.primitives = primitives;
        this.trie = trie;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        assert trie.isFrozen();
    }

    @Override
    public TypeInfo get(Class<?> clazz) {
        return get(clazz.getCanonicalName());
    }

    @Override
    public TypeInfo get(String fullyQualifiedName) {
        return get(trie, fullyQualifiedName);
    }

    static TypeInfo get(Trie<TypeInfo> trie, String fullyQualifiedName) {
        String[] split = fullyQualifiedName.split("\\.");
        List<TypeInfo> typeInfoList = trie.get(split);
        return typeInfoList == null || typeInfoList.isEmpty() ? null : typeInfoList.get(0);
    }

    @Override
    public boolean isPackagePrefix(PackagePrefix packagePrefix) {
        return trie.isStrictPrefix(packagePrefix.prefix);
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

        private final Map<TypeInfo, TypeInspectionImpl.Builder> typeInspections = new HashMap<>();
        private final Map<FieldInfo, FieldInspectionImpl.Builder> fieldInspections = new HashMap<>();
        private final Map<String, MethodInspectionImpl.Builder> methodInspections = new HashMap<>();

        private ByteCodeInspector byteCodeInspector;
        private InspectWithJavaParser inspectWithJavaParser;


        public Builder() {
            for (TypeInfo typeInfo : getPrimitives().typeByName.values()) {
                add(typeInfo, TRIGGER_BYTECODE_INSPECTION);
            }
            for (TypeInfo typeInfo : getPrimitives().primitiveByName.values()) {
                add(typeInfo, BY_HAND);
            }
            e2ImmuAnnotationExpressions.streamTypes().forEach(typeInfo -> add(typeInfo, TRIGGER_BYTECODE_INSPECTION));
        }

        public TypeMapImpl build() {
            trie.freeze();

            typeInspections.forEach((typeInfo, typeInspectionBuilder) -> {
                if (typeInspectionBuilder.finishedInspection()) {
                    if (!typeInfo.typeInspection.isSet()) {
                        typeInfo.typeInspection.set(typeInspectionBuilder.build());
                    }
                }
            });

            methodInspections.values().forEach(methodInspectionBuilder -> {
                MethodInfo methodInfo = methodInspectionBuilder.getMethodInfo();
                if (!methodInfo.methodInspection.isSet() && methodInfo.typeInfo.typeInspection.isSet()) {
                    methodInspectionBuilder.build(); // will set the inspection itself
                }
            });
            fieldInspections.forEach((fieldInfo, fieldInspectionBuilder) -> {
                if (!fieldInfo.fieldInspection.isSet() && fieldInfo.owner.typeInspection.isSet()) {
                    fieldInfo.fieldInspection.set(fieldInspectionBuilder.build());
                }
            });

            return new TypeMapImpl(trie, primitives, e2ImmuAnnotationExpressions);
        }

        public TypeInfo getOrCreate(String fullyQualifiedName, InspectionState inspectionState) {
            TypeInfo typeInfo = get(fullyQualifiedName);
            if (typeInfo != null) return typeInfo;
            TypeInfo newType = TypeInfo.fromFqn(fullyQualifiedName);
            add(newType, inspectionState);
            return newType;
        }

        public TypeInspectionImpl.Builder add(TypeInfo typeInfo, InspectionState inspectionState) {
            trie.add(typeInfo.fullyQualifiedName.split("\\."), typeInfo);
            TypeInspectionImpl.Builder inMap = typeInspections.get(typeInfo);
            if (inMap != null) {
                throw new UnsupportedOperationException();
            }
            TypeInspectionImpl.Builder ti = new TypeInspectionImpl.Builder(typeInfo, inspectionState);
            typeInspections.put(typeInfo, ti);
            return ti;
        }

        public void ensureTypeAndInspection(TypeInfo typeInfo, InspectionState inspectionState) {
            TypeInfo inMap = get(typeInfo.fullyQualifiedName);
            if (inMap == null) {
                add(typeInfo, inspectionState);
                return;
            }
            TypeInspection typeInspection = getTypeInspection(typeInfo);
            if (typeInspection == null) {
                TypeInspectionImpl.Builder ti = new TypeInspectionImpl.Builder(typeInfo, inspectionState);
                typeInspections.put(typeInfo, ti);
            }
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
        public TypeInfo get(Class<?> clazz) {
            return get(clazz.getCanonicalName());
        }

        @Override
        public TypeInfo get(String fullyQualifiedName) {
            return TypeMapImpl.get(trie, fullyQualifiedName);
        }

        @Override
        public boolean isPackagePrefix(PackagePrefix packagePrefix) {
            return trie.isStrictPrefix(packagePrefix.prefix);
        }

        @Override
        public void visit(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer) {
            trie.visit(prefix, consumer);
        }

        @Override
        public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
            return fieldInspections.get(fieldInfo);
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
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
                inspectWithJavaParser.inspect(typeInfo, typeInspection);
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
            return methodInspections.get(dn);
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
            String pathInClassPath = byteCodeInspector.getClassPath().fqnToPath(typeInfo.fullyQualifiedName, ".class");
            byteCodeInspector.inspectFromPath(pathInClassPath);
        }

        public void setByteCodeInspector(ByteCodeInspector byteCodeInspector) {
            this.byteCodeInspector = byteCodeInspector;
        }

        public void setInspectWithJavaParser(InspectWithJavaParser inspectWithJavaParser) {
            this.inspectWithJavaParser = inspectWithJavaParser;
        }
    }
}
