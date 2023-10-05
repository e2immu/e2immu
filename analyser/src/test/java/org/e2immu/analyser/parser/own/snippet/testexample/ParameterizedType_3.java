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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ParameterizedType_3 {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterizedType_3.class);

    public static final char PLUS_EXTENDS = '+';
    public static final char MINUS_SUPER = '-';
    public static final char ARRAY_BRACKET = '[';
    public static final char CHAR_L = 'L';
    public static final char TYPE_PARAM_T = 'T';
    public static final char WILDCARD_STAR = '*';
    public static final char SEMICOLON_END_NAME = ';';
    public static final char DOT = '.';
    public static final char DOLLAR_SEPARATE_SUBTYPE = '$';
    public static final char GT_END_TYPE_PARAMS = '>';
    public static final char LT_START_TYPE_PARAMS = '<';

    interface Primitives {
        ParameterizedType byteParameterizedType();

        ParameterizedType charParameterizedType();

        ParameterizedType objectParameterizedType();
    }
    interface NamedType {
    }

    record TypeParameter() implements NamedType {
    }

    static class ParameterizedType {
        static ParameterizedType WILDCARD_PARAMETERIZED_TYPE = new ParameterizedType();

        public ParameterizedType() {
            this.typeInfo = null;
        }

        private final TypeInfo typeInfo;

        public ParameterizedType(TypeInfo typeInfo,
                                 int arrays,
                                 WildCard wildCard,
                                 List<ParameterizedType> typeParameters) {
            this.typeInfo = typeInfo;
        }

        public ParameterizedType(TypeInfo typeInfo, int arrays) {
            this.typeInfo = typeInfo;
        }

        public ParameterizedType(TypeParameter namedType, int arrays, WildCard wildCard) {
            this.typeInfo = null;
        }

        enum WildCard {NONE, EXTENDS, SUPER}
    }

    interface TypeContext {
        NamedType get(String name, boolean complain);
        Primitives getPrimitives();
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

    private static class IterativeParsing {
        int startPos;
        int endPos;
        ParameterizedType result;
        boolean more;
        boolean typeNotFoundError;
    }
}
