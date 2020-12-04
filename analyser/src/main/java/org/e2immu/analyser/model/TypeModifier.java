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

import com.github.javaparser.ast.Modifier;

import java.util.Arrays;
import java.util.Set;

public enum TypeModifier {
    PUBLIC(0), PROTECTED(0), PRIVATE(0),
    // added to be able to use this type for access privileges
    PACKAGE(0),

    ABSTRACT(1), STATIC(1),

    FINAL(2), SEALED(2),
    ;

    TypeModifier(int group) {
        this.group = group;
    }

    private final int group;
    private static final int GROUPS = 3;

    public static TypeModifier from(Modifier modifier) {
        Modifier.Keyword keyword = modifier.getKeyword();
        return TypeModifier.valueOf(keyword.asString().toUpperCase());
    }

    public String toJava() {
        return name().toLowerCase();
    }

    public static String[] sort(Set<TypeModifier> modifiers) {
        TypeModifier[] array = new TypeModifier[GROUPS];
        for (TypeModifier modifier : modifiers) {
            if (array[modifier.group] != null)
                throw new UnsupportedOperationException("? already have " + array[modifier.group]);
            array[modifier.group] = modifier;
        }
        return Arrays.stream(array).filter(m -> m != null && m != PACKAGE).map(TypeModifier::toJava).toArray(String[]::new);
    }
}
