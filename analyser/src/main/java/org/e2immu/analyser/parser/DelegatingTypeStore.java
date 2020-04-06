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

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class DelegatingTypeStore implements TypeStore {

    private final TypeStore local;
    private final TypeStore delegate;

    public DelegatingTypeStore(TypeStore local, TypeStore delegate) {
        this.delegate = delegate;
        this.local = local;
    }

    @Override
    public TypeInfo getOrCreate(String fullyQualifiedName) {
        TypeInfo mine = local.get(fullyQualifiedName);
        if (mine != null) return mine;
        return delegate.getOrCreate(fullyQualifiedName);
    }

    @Override
    public TypeInfo get(String fullyQualifiedName) {
        TypeInfo mine = local.get(fullyQualifiedName);
        if (mine != null) return mine;
        return delegate.get(fullyQualifiedName);
    }

    @Override
    public void add(TypeInfo typeInfo) {
        delegate.add(typeInfo);
    }

    @Override
    public boolean isPackagePrefix(PackagePrefix packagePrefix) {
        return local.isPackagePrefix(packagePrefix) || delegate.isPackagePrefix(packagePrefix);
    }

    @Override
    public void visit(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer) {
        local.visit(prefix, consumer);
        delegate.visit(prefix, consumer);
    }

    @Override
    public void visitLeaves(String[] prefix, BiConsumer<String[], List<TypeInfo>> consumer) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitAllNewlyCreatedTypes(Consumer<TypeInfo> typeInfoConsumer) {
        delegate.visitAllNewlyCreatedTypes(typeInfoConsumer);
    }
}
