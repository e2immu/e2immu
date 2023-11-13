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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Primitives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

// signatures formally defined in https://docs.oracle.com/javase/specs/jvms/se13/html/jvms-4.html

public class ParameterizedTypeFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParameterizedTypeFactory.class);
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

    static Result from(TypeContext typeContext, LocalTypeMap findType, String signature) {
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

    // TODO extends, super

    // example with generics AND dot
    // Ljava/util/LinkedHashMap<TK;TV;>.LinkedHashIterator;
    // Ljava/util/TreeMap$NavigableSubMap<TK;TV;>.SubMapIterator<TK;>;
    // shows that we need to make this recursive or get the generics in a while loop

    private static Result normalType(TypeContext typeContext,
                                     LocalTypeMap findType,
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
                    if (iterativeParsing == null) return null;
                    typeParameters.add(iterativeParsing.result);
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
            if (!path.isEmpty()) path.append(DOLLAR_SEPARATE_SUBTYPE);
            path.append(signature, unmodifiedStart, endOfTypeInfo);
        }
        String fqn = path.toString().replaceAll("[/$]", ".");

        TypeInspection typeInspection = findType.getOrCreate(fqn, false);

        boolean unableToLoadTypeError = typeInspection == null;
        if (unableToLoadTypeError) {
            return null;
        }
        ParameterizedType parameterizedType = new ParameterizedType(typeInspection.typeInfo(), arrays, wildCard,
                typeParameters);
        return new Result(parameterizedType, semiColon + 1, typeNotFoundError);
    }

    private static ParameterizedType primitive(Primitives primitives, char firstChar) {
        return switch (firstChar) {
            case 'B' -> primitives.byteParameterizedType();
            case 'C' -> primitives.charParameterizedType();
            case 'D' -> primitives.doubleParameterizedType();
            case 'F' -> primitives.floatParameterizedType();
            case 'I' -> primitives.intParameterizedType();
            case 'J' -> primitives.longParameterizedType();
            case 'S' -> primitives.shortParameterizedType();
            case 'V' -> primitives.voidParameterizedType();
            case 'Z' -> primitives.booleanParameterizedType();
            default -> throw new RuntimeException("Char " + firstChar + " does NOT represent a primitive!");
        };
    }

    private static class IterativeParsing {
        int startPos;
        int endPos;
        ParameterizedType result;
        boolean more;
        boolean typeNotFoundError;
    }

    private static IterativeParsing iterativelyParseTypes(TypeContext typeContext,
                                                          LocalTypeMap findType,
                                                          String signature,
                                                          IterativeParsing iterativeParsing) {
        ParameterizedTypeFactory.Result result = ParameterizedTypeFactory.from(typeContext, findType,
                signature.substring(iterativeParsing.startPos));
        if (result == null) return null;
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
