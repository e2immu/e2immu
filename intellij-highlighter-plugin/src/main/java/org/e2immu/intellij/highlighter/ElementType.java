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

package org.e2immu.intellij.highlighter;

import java.util.StringJoiner;

public enum ElementType {
    TYPE_OF_METHOD("-mt"),
    METHOD("-m"),
    FIELD("-f"),
    TYPE_OF_FIELD("-ft"),
    PARAM("-p"),
    TYPE("-t");

    private final String extension;

    ElementType(String extension) {
        this.extension = extension;
    }

    @Override
    public String toString() {
        return extension;
    }
}
