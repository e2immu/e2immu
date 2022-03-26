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

    @Override
    public String minimal() {
        return switch (required) {
            case SIMPLE -> simpleName;
            case FQN -> fullyQualifiedName;
            case QUALIFIED_FROM_PRIMARY_TYPE -> fromPrimaryTypeDownwards;
        };
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
