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

package org.e2immu.analyser.parser;

import org.e2immu.analyser.model.PackagePrefix;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.util.Trie;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The type store is meant to contain all "independent" types exactly once.
 * Classes, interfaces, enums that have a fully qualified name.
 */
public class MapBasedTypeStore implements TypeStore {

    private Trie<TypeInfo> trie = new Trie<>();
    private final List<TypeInfo> newlyCreatedTypes = new LinkedList<>();

    public TypeInfo getOrCreate(String fullyQualifiedName) {
        String[] split = fullyQualifiedName.split("\\.");
        List<TypeInfo> typeInfoList = trie.getOrCompute(split, strings -> {
            TypeInfo typeInfo = new TypeInfo(fullyQualifiedName);
            synchronized (newlyCreatedTypes) {
                newlyCreatedTypes.add(typeInfo);
            }
            return typeInfo;
        });
        return typeInfoList.get(0);
    }

    public TypeInfo get(String fullyQualifiedName) {
        String[] split = fullyQualifiedName.split("\\.");
        List<TypeInfo> typeInfoList = trie.get(split);
        return typeInfoList == null || typeInfoList.isEmpty() ? null : typeInfoList.get(0);
    }

    public void add(TypeInfo typeInfo) {
        trie.add(typeInfo.fullyQualifiedName.split("\\."), typeInfo);
    }

    @Override
    public boolean containsPrefix(String fullyQualifiedName) {
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

    public boolean isPackagePrefix(PackagePrefix packagePrefix) {
        return trie.isStrictPrefix(packagePrefix.prefix);
    }

    public void visit(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer) {
        trie.visit(prefix, consumer);
    }

    public void visitLeaves(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer) {
        trie.visitLeaves(prefix, consumer);
    }

    /**
     * it is critical that the map is copied and traversed independently of the <code>newlyCreatedTypes</code>
     * field, as the consumer will probably modify it!
     *
     * @param typeInfoConsumer receiver of the types
     */
    @Override
    public void visitAllNewlyCreatedTypes(Consumer<TypeInfo> typeInfoConsumer) {
        List<TypeInfo> toIterate;
        synchronized (newlyCreatedTypes) {
            toIterate = new ArrayList<>(newlyCreatedTypes);
            newlyCreatedTypes.clear();
        }
        toIterate.forEach(typeInfoConsumer);
    }
}
