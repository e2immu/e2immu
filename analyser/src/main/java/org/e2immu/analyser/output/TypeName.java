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

package org.e2immu.analyser.output;

import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.util.StringUtil;

public record TypeName(String simpleName,
                       String fullyQualifiedName,
                       String fromPrimaryTypeDownwards,
                       Required required) implements Qualifier {

    public enum Required {
        FQN, QUALIFIED_FROM_PRIMARY_TYPE, SIMPLE
    }

    // for tests
    public TypeName(String simpleName) {
        this(simpleName, simpleName, simpleName, Required.SIMPLE);
    }

    public TypeName(TypeInfo typeInfo, Required requiresQualifier) {
        this(typeInfo.simpleName, typeInfo.fullyQualifiedName,
                typeInfo.isPrimaryType() ? typeInfo.simpleName : typeInfo.fromPrimaryTypeDownwards(),
                requiresQualifier);
    }

    @Override
    public String minimal() {
        return switch (required) {
            case SIMPLE -> simpleName;
            case FQN -> fullyQualifiedName;
            case QUALIFIED_FROM_PRIMARY_TYPE -> fromPrimaryTypeDownwards;
        };
    }

    @Override
    public String debug() {
        return fullyQualifiedName;
    }

    @Override
    public int length(FormattingOptions options) {
        return minimal().length();
    }

    @Override
    public String write(FormattingOptions options) {
        return minimal();
    }

    @Override
    public String generateJavaForDebugging() {
        return ".add(new TypeName(" + StringUtil.quote(simpleName) + "))";
    }
}
