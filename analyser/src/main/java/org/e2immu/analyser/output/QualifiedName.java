/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.output;

import org.e2immu.analyser.util.StringUtil;

public record QualifiedName(String name, Qualifier qualifier, Required qualifierRequired) implements Qualifier {

    // for tests
    public QualifiedName(String name) {
        this(name, null, Required.NEVER);
    }

    public enum Required {
        YES, // always write
        NO_FIELD, // don't write unless a field-related option says so
        NO_METHOD, // don't write unless a method-related option says so
        NEVER // never write
    }

    @Override
    public String minimal() {
        return qualifierRequired == Required.YES ? qualifier.minimal() + "." + name : name;
    }

    @Override
    public int length(FormattingOptions options) {
        return minimal().length();
    }

    @Override
    public String write(FormattingOptions options) {
        if (options.allFieldsRequireThis() && qualifierRequired == Required.NO_FIELD && qualifier instanceof ThisName) {
            return qualifier().write(options) + "." + name;
        }
        if (options.allStaticFieldsRequireType() && qualifierRequired != Required.NO_FIELD && qualifier instanceof TypeName) {
            return qualifier().write(options) + "." + name;
        }
        return minimal();
    }

    @Override
    public String generateJavaForDebugging() {
        String q = qualifier == null ? "null" : StringUtil.quote(qualifier.minimal());
        return ".add(new QualifiedName(" + StringUtil.quote(name) + ", " + q + ", " + qualifierRequired + "))";
    }
}
