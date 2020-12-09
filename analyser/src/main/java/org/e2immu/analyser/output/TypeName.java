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

public record TypeName(String simpleName, String fullyQualifiedName,
                       String distinguishingName) implements OutputElement {

    public TypeName(TypeInfo typeInfo) {
        this(typeInfo.simpleName, typeInfo.fullyQualifiedName, typeInfo.fullyQualifiedName);
    }

    @Override
    public String minimal() {
        return simpleName;
    }

    @Override
    public String debug() {
        return fullyQualifiedName;
    }

    @Override
    public int length(FormattingOptions options) {
        return simpleName.length(); // TODO
    }

    @Override
    public String write(FormattingOptions options) {
        return simpleName;
    }

    @Override
    public String generateJavaForDebugging() {
        return ".add(new TypeName(" + StringUtil.quote(simpleName) + "," + StringUtil.quote(fullyQualifiedName) + ","
                + StringUtil.quote(distinguishingName) + "))";
    }
}
