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

import org.e2immu.analyser.util.StringUtil;

public record VariableName(String name, Qualifier qualifier, Required qualifierRequired) implements Qualifier {

    // for tests
    public VariableName(String name) {
        this(name, null, Required.NEVER);
    }

    public enum Required {
        YES, NO, NEVER
    }

    @Override
    public String minimal() {
        return qualifierRequired == Required.YES ? qualifier.minimal() + "." + name : name;
    }

    @Override
    public String debug() {
        if (qualifier != null) {
            return qualifier.debug() + "." + name;
        }
        return name;
    }

    @Override
    public int length(FormattingOptions options) {
        return options.debug() ? debug().length() : minimal().length();
    }

    @Override
    public String write(FormattingOptions options) {
        if (options.allFieldsRequireThis() && qualifierRequired != Required.NEVER && qualifier instanceof ThisName) {
            return qualifier().write(options) + "." + name;
        }
        if (options.allStaticFieldsRequireType() && qualifierRequired != Required.NEVER && qualifier instanceof TypeName) {
            return qualifier().write(options) + "." + name;
        }
        return minimal();
    }

    @Override
    public String generateJavaForDebugging() {
        String q = qualifier == null ? "null" : StringUtil.quote(qualifier.minimal());
        return ".add(new VariableName(" + StringUtil.quote(name) + ", " + q + ", " + qualifierRequired + "))";
    }
}
