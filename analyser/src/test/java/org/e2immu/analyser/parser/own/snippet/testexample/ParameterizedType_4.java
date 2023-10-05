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

package org.e2immu.analyser.parser.own.snippet.testexample;

import org.e2immu.annotation.Independent;

import java.util.ArrayList;
import java.util.List;

// heavily trimmed down version of _7; trying to catch NoProgressException
public class ParameterizedType_4 {

    static class ParameterizedType {
        private final TypeInfo typeInfo;

        public ParameterizedType(TypeInfo typeInfo, int arrays, WildCard wildCard,
                                 List<ParameterizedType> typeParameters) {
            this.typeInfo = typeInfo;
        }

        enum WildCard {NONE, EXTENDS, SUPER}
    }

    record TypeInfo(String fqn) {
    }

    static class Result {
        final ParameterizedType parameterizedType;
        final int nextPos;
        final boolean typeNotFoundError;

        private Result(ParameterizedType parameterizedType, int nextPos, boolean error) {
            this.nextPos = nextPos;
            this.parameterizedType = parameterizedType;
            typeNotFoundError = error;
        }
    }

    @Independent
    public interface FindType {
        TypeInfo find(String fqn, String path);
    }

    private static Result normalType(FindType findType, int arrays, ParameterizedType.WildCard wildCard) {
        StringBuilder path = new StringBuilder();
        List<ParameterizedType> typeParameters = new ArrayList<>();

        String fqn = path.toString().replaceAll("[/$]", ".");
        TypeInfo typeInfo = findType.find(fqn, path.toString());

        ParameterizedType parameterizedType = new ParameterizedType(typeInfo, arrays, wildCard, typeParameters);
        return new Result(parameterizedType, 0, false);
    }
}
