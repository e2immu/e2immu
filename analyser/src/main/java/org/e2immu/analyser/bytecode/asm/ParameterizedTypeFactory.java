/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.model.NamedType;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeParameter;
import org.e2immu.analyser.parser.TypeContext;

import java.util.ArrayList;
import java.util.List;

import static org.e2immu.analyser.parser.Primitives.PRIMITIVES;

// signatures formally defined in https://docs.oracle.com/javase/specs/jvms/se13/html/jvms-4.html

public class ParameterizedTypeFactory {

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

    // for testing
    static Result from(TypeContext typeContext, String signature) {
        return from(typeContext, (fqn, path) -> typeContext.typeStore.getOrCreate(fqn), signature);
    }

    static Result from(TypeContext typeContext, FindType findType, String signature) {
        int firstCharPos = 0;
        char firstChar = signature.charAt(0);

        // wildcard, <?>
        if ('*' == firstChar) {
            return new Result(ParameterizedType.WILDCARD_PARAMETERIZED_TYPE, 1, false);
        }

        ParameterizedType.WildCard wildCard;
        // extends keyword; NOTE: order is important, extends and super need to come before arrays
        if ('+' == firstChar || ':' == firstChar) {
            firstCharPos++;
            firstChar = signature.charAt(firstCharPos);
            wildCard = ParameterizedType.WildCard.EXTENDS;
        } else if ('-' == firstChar) {
            firstCharPos++;
            firstChar = signature.charAt(firstCharPos);
            wildCard = ParameterizedType.WildCard.SUPER;
        } else wildCard = ParameterizedType.WildCard.NONE;

        // arrays
        int arrays = 0;
        while ('[' == firstChar) {
            arrays++;
            firstCharPos++;
            firstChar = signature.charAt(firstCharPos);
        }

        // normal class or interface type
        if ('L' == firstChar) {
            return normalType(typeContext, findType, signature, arrays, wildCard, firstCharPos);
        }

        // type parameter
        if ('T' == firstChar) {
            int semiColon = signature.indexOf(';');
            String typeParamName = signature.substring(firstCharPos + 1, semiColon);
            NamedType namedType = typeContext.get(typeParamName, false);
            if (namedType == null) {
                // this is possible
                // <T:Ljava/lang/Object;T_SPLITR::Ljava/util/Spliterator$OfPrimitive<TT;TT_CONS;TT_SPLITR;>;T_CONS:Ljava/lang/Object;>Ljava/util/stream/StreamSpliterators$SliceSpliterator<TT;TT_SPLITR;>;Ljava/util/Spliterator$OfPrimitive<TT;TT_CONS;TT_SPLITR;>;
                // problem is that T_CONS is used before it is declared
                return new Result(PRIMITIVES.objectParameterizedType, semiColon + 1, true);
            }
            if (!(namedType instanceof TypeParameter))
                throw new RuntimeException("?? expected " + typeParamName + " to be a type parameter");
            return new Result(new ParameterizedType((TypeParameter) namedType,
                    arrays, wildCard), semiColon + 1, false);
        }
        ParameterizedType primitivePt = primitive(firstChar);
        if (arrays > 0) {
            return new Result(new ParameterizedType(primitivePt.typeInfo, arrays), arrays + 1, false);
        }
        return new Result(primitivePt, 1, false);
    }

    // TODO extends, super

    // example with generics AND dot
    // Ljava/util/LinkedHashMap<TK;TV;>.LinkedHashIterator;
    // Ljava/util/TreeMap$NavigableSubMap<TK;TV;>.SubMapIterator<TK;>;
    // shows that we need to make this recursive or get the generics in a while loop

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
            semiColon = signature.indexOf(';', start);
            int openGenerics = signature.indexOf('<', start);
            boolean haveGenerics = openGenerics >= 0 && openGenerics < semiColon;
            int endOfTypeInfo;
            int unmodifiedStart = start;
            if (haveGenerics) {
                endOfTypeInfo = openGenerics;
                IterativeParsing iterativeParsing = new IterativeParsing();
                iterativeParsing.startPos = openGenerics + 1;
                do {
                    iterativeParsing = iterativelyParseTypes(typeContext, findType, signature, iterativeParsing);
                    if (!iterativeParsing.result.isTypeParameter() || !typeParameters.contains(iterativeParsing.result)) {
                        typeParameters.add(iterativeParsing.result);
                    }
                    typeNotFoundError = typeNotFoundError || iterativeParsing.typeNotFoundError;
                } while (iterativeParsing.more);
                haveDot = iterativeParsing.endPos < signature.length() && signature.charAt(iterativeParsing.endPos) == '.';
                if (haveDot) start = iterativeParsing.endPos + 1;
                semiColon = signature.indexOf(';', iterativeParsing.endPos);
            } else {
                int dot = signature.indexOf('.', start);
                haveDot = dot >= 0 && dot < semiColon;
                if (haveDot) start = dot + 1;
                endOfTypeInfo = dot >= 0 ? dot : semiColon;
            }
            if (path.length() > 0) path.append("$");
            path.append(signature, unmodifiedStart, endOfTypeInfo);
        }
        String fqn = path.toString().replaceAll("[/$]", ".");

        TypeInfo typeInfo = findType.find(fqn, path.toString());
        ParameterizedType parameterizedType = new ParameterizedType(typeInfo, arrays, wildCard, typeParameters);
        return new Result(parameterizedType, semiColon + 1, typeNotFoundError);
    }

    private static ParameterizedType primitive(char firstChar) {
        switch (firstChar) {
            case 'B':
                return PRIMITIVES.byteParameterizedType;
            case 'C':
                return PRIMITIVES.charParameterizedType;
            case 'D':
                return PRIMITIVES.doubleParameterizedType;
            case 'F':
                return PRIMITIVES.floatParameterizedType;
            case 'I':
                return PRIMITIVES.intParameterizedType;
            case 'J':
                return PRIMITIVES.longParameterizedType;
            case 'S':
                return PRIMITIVES.shortParameterizedType;
            case 'V':
                return PRIMITIVES.voidParameterizedType;
            case 'Z':
                return PRIMITIVES.booleanParameterizedType;
            default:
                throw new RuntimeException("Char " + firstChar + " does NOT represent a primitive!");
        }
    }

    private static class IterativeParsing {
        int startPos;
        int endPos;
        ParameterizedType result;
        boolean more;
        boolean typeNotFoundError;
    }

    private static IterativeParsing iterativelyParseTypes(TypeContext typeContext,
                                                          FindType findType,
                                                          String signature,
                                                          IterativeParsing iterativeParsing) {
        ParameterizedTypeFactory.Result result = ParameterizedTypeFactory.from(typeContext, findType,
                signature.substring(iterativeParsing.startPos));
        int end = iterativeParsing.startPos + result.nextPos;
        IterativeParsing next = new IterativeParsing();
        next.result = result.parameterizedType;
        char atEnd = signature.charAt(end);
        if (atEnd == '>') {
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
