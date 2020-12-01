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
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public enum MethodModifier {
    PUBLIC(0), PRIVATE(0), PROTECTED(0),
    ABSTRACT(1), DEFAULT(1),
    FINAL(2), STATIC(2),
    SYNCHRONIZED(3);

    private final int group;
    private static final int GROUPS = 4;

    MethodModifier(int group) {
        this.group = group;
    }

    public static MethodModifier from(Modifier modifier) {
        return MethodModifier.valueOf(modifier.getKeyword().asString().toUpperCase());
    }

    public String toJava() {
        return name().toLowerCase();
    }

    public static String toJava(Set<MethodModifier> modifiers) {
        MethodModifier[] array = new MethodModifier[GROUPS];
        for (MethodModifier methodModifier : modifiers) {
            if (array[methodModifier.group] != null)
                throw new UnsupportedOperationException("? already have " + array[methodModifier.group]);
            array[methodModifier.group] = methodModifier;
        }
        return Arrays.stream(array).filter(Objects::nonNull).map(MethodModifier::toJava).collect(Collectors.joining(" "))
                + (modifiers.isEmpty() ? "" : " ");
    }
}
