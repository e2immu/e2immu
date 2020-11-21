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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.util.Trie;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class TypeMapImpl implements TypeMap {

    private final Trie<TypeInfo> trie;
    private final Primitives primitives;

    private TypeMapImpl(Trie<TypeInfo> trie, Primitives primitives) {
        this.primitives = primitives;
        this.trie = trie;
        assert trie.isFrozen();
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

    @Override
    public void visitLeaves(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer) {
        trie.visitLeaves(prefix, consumer);
    }

    @Override
    public boolean containsPrefix(String fullyQualifiedName) {
        return containsPrefix(trie, fullyQualifiedName);
    }

    static boolean containsPrefix(Trie<TypeInfo> trie, String fullyQualifiedName) {
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

        private final Map<TypeInfo, TypeInspectionImpl.Builder> typeInspections = new HashMap<>();
        private final Map<FieldInfo, FieldInspectionImpl.Builder> fieldInspections = new HashMap<>();
        private final Map<MethodInfo, MethodInspectionImpl.Builder> methodInspections = new HashMap<>();

        public TypeMapImpl build() {
            trie.freeze();
            return new TypeMapImpl(trie, primitives);
        }

        public TypeInfo getOrCreate(String fullyQualifiedName, int inspectionState) {
            TypeInfo typeInfo = get(fullyQualifiedName);
            if (typeInfo != null) return typeInfo;
            TypeInfo newType = TypeInfo.fromFqn(fullyQualifiedName);
            add(newType, inspectionState);
            return newType;
        }

        public TypeInspectionImpl.Builder add(TypeInfo typeInfo, int inspectionState) {
            trie.add(typeInfo.fullyQualifiedName.split("\\."), typeInfo);
            TypeInspectionImpl.Builder ti = new TypeInspectionImpl.Builder(typeInfo, inspectionState);
            if (typeInspections.put(typeInfo, ti) != null) {
                throw new IllegalArgumentException("Re-registering type " + typeInfo.fullyQualifiedName);
            }
            return ti;
        }

        public void registerFieldInspection(FieldInfo fieldInfo, FieldInspectionImpl.Builder builder) {
            if (fieldInspections.put(fieldInfo, builder) != null) {
                throw new IllegalArgumentException("Re-registering field " + fieldInfo.fullyQualifiedName());
            }
        }

        public void registerMethodInspection(MethodInspectionImpl.Builder builder) {
            if (methodInspections.put(builder.getMethodInfo(), builder) != null) {
                throw new IllegalArgumentException("Re-registering method " + builder.getFullyQualifiedName());
            }
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
        public void visitLeaves(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer) {
            trie.visitLeaves(prefix, consumer);
        }

        @Override
        public boolean containsPrefix(String fullyQualifiedName) {
            return TypeMapImpl.containsPrefix(trie, fullyQualifiedName);
        }

        @Override
        public FieldInspection getFieldInspection(FieldInfo fieldInfo) {
            return fieldInspections.get(fieldInfo);
        }

        @Override
        public TypeInspection getTypeInspection(TypeInfo typeInfo) {
            return typeInspections.get(typeInfo);
        }

        @Override
        public MethodInspection getMethodInspection(MethodInfo methodInfo) {
            return methodInspections.get(methodInfo);
        }

        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        public Stream<Map.Entry<TypeInfo, TypeInspectionImpl.Builder>> streamTypes() {
            return typeInspections.entrySet().stream();
        }
    }
}
