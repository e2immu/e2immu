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
        DOLLARIZED_FQN, // com.foo.Bar$Bar2
        FQN, // com.foo.Bar.Bar2
        QUALIFIED_FROM_PRIMARY_TYPE, // Bar.Bar2
        SIMPLE // Bar2
    }

    // for tests
    public TypeName(String simpleName) {
        this(simpleName, simpleName, simpleName, Required.SIMPLE);
    }

    public TypeName {
        assert simpleName != null;
        assert fullyQualifiedName != null;
        assert fromPrimaryTypeDownwards != null;
        assert required != null;
    }

    @Override
    public String minimal() {
        return switch (required) {
            case SIMPLE -> simpleName;
            case FQN -> fullyQualifiedName;
            case QUALIFIED_FROM_PRIMARY_TYPE -> fromPrimaryTypeDownwards;
            case DOLLARIZED_FQN ->
                    fullyQualifiedName.substring(0, fullyQualifiedName.length() - fromPrimaryTypeDownwards.length())
                            + fromPrimaryTypeDownwards.replace(".", "$");
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
