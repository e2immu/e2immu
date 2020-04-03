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

package org.e2immu.analyser.model;

public class PackagePrefix implements NamedType {
    public final String[] prefix;

    public PackagePrefix(String[] prefix) {
        this.prefix = prefix;
    }

    public PackagePrefix append(String component) {
        String[] newPrefix = new String[prefix.length + 1];
        System.arraycopy(prefix, 0, newPrefix, 0, prefix.length);
        newPrefix[prefix.length] = component;
        return new PackagePrefix(newPrefix);
    }

    @Override
    public String simpleName() {
        return String.join(",", prefix);
    }
}
