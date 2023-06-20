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

import org.e2immu.annotation.ImmutableContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ParameterizedType_2 {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterizedType_2.class);

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

    @ImmutableContainer
    interface Primitives {
        ParameterizedType byteParameterizedType();

        ParameterizedType charParameterizedType();

        ParameterizedType objectParameterizedType();
    }

    @ImmutableContainer
    interface NamedType {
    }

    record TypeParameter() implements NamedType {
    }

    static class ParameterizedType {
        private static ParameterizedType WILDCARD_PARAMETERIZED_TYPE = new ParameterizedType();

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

    @ImmutableContainer
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

    @ImmutableContainer
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

    static Result from(TypeContext typeContext, FindType findType, String signature) {
        try {
            int firstCharPos = 0;
            char firstChar = signature.charAt(0);

            // wildcard, <?>
            if (WILDCARD_STAR == firstChar) {
                return new Result(ParameterizedType.WILDCARD_PARAMETERIZED_TYPE, 1, false);
            }

            ParameterizedType.WildCard wildCard;
            // extends keyword; NOTE: order is important, extends and super need to come before arrays
            if (PLUS_EXTENDS == firstChar) {
                firstCharPos++;
                firstChar = signature.charAt(firstCharPos);
                wildCard = ParameterizedType.WildCard.EXTENDS;
            } else if (MINUS_SUPER == firstChar) {
                firstCharPos++;
                firstChar = signature.charAt(firstCharPos);
                wildCard = ParameterizedType.WildCard.SUPER;
            } else wildCard = ParameterizedType.WildCard.NONE;

            // arrays
            int arrays = 0;
            while (ARRAY_BRACKET == firstChar) {
                arrays++;
                firstCharPos++;
                firstChar = signature.charAt(firstCharPos);
            }

            // normal class or interface type
            if (CHAR_L == firstChar) {
                return normalType(typeContext, findType, signature, arrays, wildCard, firstCharPos);
            }

            // type parameter
            if (TYPE_PARAM_T == firstChar) {
                int semiColon = signature.indexOf(SEMICOLON_END_NAME);
                String typeParamName = signature.substring(firstCharPos + 1, semiColon);
                NamedType namedType = typeContext.get(typeParamName, false);
                if (namedType == null) {
                    // this is possible
                    // <T:Ljava/lang/Object;T_SPLITR::Ljava/util/Spliterator$OfPrimitive<TT;TT_CONS;TT_SPLITR;>;T_CONS:Ljava/lang/Object;>Ljava/util/stream/StreamSpliterators$SliceSpliterator<TT;TT_SPLITR;>;Ljava/util/Spliterator$OfPrimitive<TT;TT_CONS;TT_SPLITR;>;
                    // problem is that T_CONS is used before it is declared
                    ParameterizedType objectParameterizedType = typeContext.getPrimitives().objectParameterizedType();
                    return new Result(objectParameterizedType, semiColon + 1, true);
                }
                if (!(namedType instanceof TypeParameter))
                    throw new RuntimeException("?? expected " + typeParamName + " to be a type parameter");
                return new Result(new ParameterizedType((TypeParameter) namedType,
                        arrays, wildCard), semiColon + 1, false);
            }
            ParameterizedType primitivePt = primitive(typeContext.getPrimitives(), firstChar);
            if (arrays > 0) {
                return new Result(new ParameterizedType(primitivePt.typeInfo, arrays), arrays + 1, false);
            }
            return new Result(primitivePt, 1, false);
        } catch (RuntimeException e) {
            LOGGER.error("Caught exception while parsing type from " + signature);
            throw e;
        }
    }

    private static Result normalType(TypeContext typeContext,
                                     FindType findType,
                                     String signature,
                                     int arrays,
                                     ParameterizedType.WildCard wildCard,
                                     int firstCharIndex) {
        StringBuilder path = new StringBuilder();
        int semiColon = -1;
        int start = firstCharIndex + 1;
        List<ParameterizedType> typeParameters = new ArrayList<>();
        boolean haveDot = true;
        boolean typeNotFoundError = false;

        while (haveDot) {
            semiColon = signature.indexOf(SEMICOLON_END_NAME, start);
            int openGenerics = signature.indexOf(LT_START_TYPE_PARAMS, start);
            boolean haveGenerics = openGenerics >= 0 && openGenerics < semiColon;
            int endOfTypeInfo;
            int unmodifiedStart = start;
            if (haveGenerics) {
                endOfTypeInfo = openGenerics;
                IterativeParsing iterativeParsing = new IterativeParsing();
                iterativeParsing.startPos = openGenerics + 1;
                do {
                    iterativeParsing = iterativelyParseTypes(typeContext, findType, signature, iterativeParsing);
                    //if (!iterativeParsing.result.isTypeParameter() || !typeParameters.contains(iterativeParsing.result)) {
                    typeParameters.add(iterativeParsing.result);
                    //} we should be repeating, e.g., BinaryOperator<T> extends BiFunction<T,T,T>
                    typeNotFoundError = typeNotFoundError || iterativeParsing.typeNotFoundError;
                } while (iterativeParsing.more);
                haveDot = iterativeParsing.endPos < signature.length() && signature.charAt(iterativeParsing.endPos) == DOT;
                if (haveDot) start = iterativeParsing.endPos + 1;
                semiColon = signature.indexOf(SEMICOLON_END_NAME, iterativeParsing.endPos);
            } else {
                int dot = signature.indexOf(DOT, start);
                haveDot = dot >= 0 && dot < semiColon;
                if (haveDot) start = dot + 1;
                endOfTypeInfo = haveDot ? dot : semiColon;
            }
            if (path.length() > 0) path.append(DOLLAR_SEPARATE_SUBTYPE);
            path.append(signature, unmodifiedStart, endOfTypeInfo);
        }
        String fqn = path.toString().replaceAll("[/$]", ".");

        TypeInfo typeInfo = findType.find(fqn, path.toString());
        boolean unableToLoadTypeError = typeInfo == null;
        if (unableToLoadTypeError) {
            return null;
        }
        ParameterizedType parameterizedType = new ParameterizedType(typeInfo, arrays, wildCard, typeParameters);
        return new Result(parameterizedType, semiColon + 1, typeNotFoundError);
    }


    private static ParameterizedType primitive(Primitives primitives, char firstChar) {
        return switch (firstChar) {
            case 'B' -> primitives.byteParameterizedType();
            case 'C' -> primitives.charParameterizedType();
            default -> throw new RuntimeException("Char " + firstChar + " does NOT represent a primitive!");
        };
    }

    private static IterativeParsing iterativelyParseTypes(TypeContext typeContext,
                                                          FindType findType,
                                                          String signature,
                                                          IterativeParsing iterativeParsing) {
        Result result = from(typeContext, findType, signature.substring(iterativeParsing.startPos));
        int end = iterativeParsing.startPos + result.nextPos;
        IterativeParsing next = new IterativeParsing();
        next.result = result.parameterizedType;
        char atEnd = signature.charAt(end);
        if (atEnd == GT_END_TYPE_PARAMS) {
            next.more = false;
            next.endPos = end + 1;
        } else {
            next.more = true;
            next.startPos = end;
        }
        next.typeNotFoundError = iterativeParsing.typeNotFoundError || result.typeNotFoundError;
        return next;
    }
}
