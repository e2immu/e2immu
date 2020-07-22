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
import org.e2immu.annotation.*;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@E2Container(after = "freeze")
public interface TypeStore {
    
    @Only(before = "freeze")
    @Modified
    TypeInfo getOrCreate(String fullyQualifiedName);

    @NotModified
    TypeInfo get(String fullyQualifiedName);

    @Only(before = "freeze")
    void add(TypeInfo typeInfo);

    @NotModified
    boolean isPackagePrefix(PackagePrefix packagePrefix);

    @NotModified
    void visit(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer);

    @NotModified
    void visitLeaves(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer);

    @Only(before = "freeze")
    @Modified
    void visitAllNewlyCreatedTypes(Consumer<TypeInfo> typeInfoConsumer);

    @NotModified
    boolean containsPrefix(String fullyQualifiedName);

    @Mark("freeze")
    void freeze();
}
