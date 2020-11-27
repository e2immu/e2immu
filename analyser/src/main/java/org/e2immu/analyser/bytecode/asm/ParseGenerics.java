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

package org.e2immu.analyser.bytecode.asm;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.inspector.MethodInspectionImpl;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.inspector.TypeInspectionImpl;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeParameter;
import org.e2immu.analyser.model.TypeParameterImpl;
import org.e2immu.analyser.parser.Primitives;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

class ParseGenerics {
    public static final char COLON = ':';
    public static final char GT_END_TYPE_PARAMS = '>';
    public static final char CARET_THROWS = '^';
    public static final char CLOSE_BRACKET = ')';

    private final TypeContext typeContext;
    private final TypeInfo typeInfo;
    private final TypeInspectionImpl.Builder typeInspectionBuilder;
    private final FindType findType; // getOrCreateTypeInfo for method-related generics; mustFindTypeInfo for type generics

    ParseGenerics(TypeContext typeContext, TypeInfo typeInfo, TypeInspectionImpl.Builder typeInspectionBuilder, FindType findType) {
        this.typeContext = typeContext;
        this.typeInfo = typeInfo;
        this.typeInspectionBuilder = typeInspectionBuilder;
        this.findType = findType;
    }

    private static class IterativeParsing<R> {
        int startPos;
        int endPos;
        R result;
        boolean more;
        String name;
        boolean typeNotFoundError;
    }

    int parseTypeGenerics(String signature) {
        IterativeParsing<TypeParameter> iterativeParsing = new IterativeParsing<>();
        while (true) {
            iterativeParsing.startPos = 1;
            AtomicInteger index = new AtomicInteger();
            do {
                iterativeParsing = iterativelyParseGenerics(signature,
                        iterativeParsing,
                        name -> {
                            TypeParameterImpl typeParameter = new TypeParameterImpl(typeInfo, name, index.getAndIncrement());
                            typeContext.addToContext(typeParameter);
                            typeInspectionBuilder.addTypeParameter(typeParameter);
                            return typeParameter;
                        },
                        typeContext,
                        findType);
                if (iterativeParsing == null) {
                    return -1; // error state
                }
            } while (iterativeParsing.more);
            if (!iterativeParsing.typeNotFoundError) break;
            iterativeParsing = new IterativeParsing<>();
        }
        return iterativeParsing.endPos;
    }

    private IterativeParsing<TypeParameter> iterativelyParseGenerics(String signature,
                                                      IterativeParsing<TypeParameter> iterativeParsing,
                                                      Function<String, TypeParameterImpl> createTypeParameterAndAddToContext,
                                                      TypeContext typeContext,
                                                      FindType findType) {
        int end = signature.indexOf(COLON, iterativeParsing.startPos);
        char atEnd = COLON;

        boolean typeNotFoundError = iterativeParsing.typeNotFoundError;
        // example for extends keyword: sig='<T::Ljava/lang/annotation/Annotation;>(Ljava/lang/Class<TT;>;)TT;' for
        // method getAnnotation in java.lang.reflect.AnnotatedElement

        String name = signature.substring(iterativeParsing.startPos, end);
        TypeParameterImpl typeParameter = createTypeParameterAndAddToContext.apply(name);
        List<ParameterizedType> typeBounds = new ArrayList<>();

        IterativeParsing<TypeParameter> next = new IterativeParsing<>();
        next.name = name;

        while (atEnd == COLON) {
            char charAfterColon = signature.charAt(end + 1);
            if (charAfterColon == COLON) { // this can happen max. once, when there is no class extension, but there are interface extensions
                end++;
            }
            ParameterizedTypeFactory.Result result = ParameterizedTypeFactory.from(typeContext, findType,
                    signature.substring(end + 1));
            if (result == null) return null; // unable to load type
            if (result.parameterizedType.typeInfo != null && !Primitives.isJavaLangObject(result.parameterizedType.typeInfo)) {
                typeBounds.add(result.parameterizedType);
            }

            end = result.nextPos + end + 1;
            atEnd = signature.charAt(end);

            next.typeNotFoundError = typeNotFoundError || result.typeNotFoundError;
        }


        typeParameter.setTypeBounds(ImmutableList.copyOf(typeBounds));
        next.result = typeParameter;


        if (GT_END_TYPE_PARAMS == atEnd) {
            next.more = false;
            next.endPos = end;
        } else {
            next.more = true;
            next.startPos = end;
        }
        return next;
    }


    // result should be
    // entrySet()                                       has a complicated return type, but that is skipped
    // addFirst(E)                                      type parameter of interface/class as first argument
    // ArrayList(java.util.Collection<? extends E>)     this is a constructor
    // copyOf(U[], int, java.lang.Class<? extends T[]>) spaces between parameter types

    int parseMethodGenerics(String signature,
                            MethodInspectionImpl.Builder methodInspectionBuilder,
                            TypeContext methodContext) {
        IterativeParsing<TypeParameter> iterativeParsing = new IterativeParsing<>();
        while (true) {
            iterativeParsing.startPos = 1;
            AtomicInteger index = new AtomicInteger();
            do {
                iterativeParsing = iterativelyParseGenerics(signature,
                        iterativeParsing, name -> {
                            TypeParameterImpl typeParameter = new TypeParameterImpl(name, index.getAndIncrement());
                            methodInspectionBuilder.addTypeParameter(typeParameter);
                            methodContext.addToContext(typeParameter);
                            return typeParameter;
                        },
                        methodContext,
                        findType);
                if (iterativeParsing == null) {
                    return -1; // error state
                }
            } while (iterativeParsing.more);
            if (!iterativeParsing.typeNotFoundError) break;
            iterativeParsing = new IterativeParsing<>();
        }
        return iterativeParsing.endPos;
    }

    List<ParameterizedType> parseParameterTypesOfMethod(TypeContext typeContext, String signature) {
        if (signature.startsWith("()")) {
            return List.of(ParameterizedTypeFactory.from(typeContext,
                    findType, signature.substring(2)).parameterizedType);
        }
        List<ParameterizedType> methodTypes = new ArrayList<>();

        IterativeParsing<ParameterizedType> iterativeParsing = new IterativeParsing<>();
        iterativeParsing.startPos = 1;
        do {
            iterativeParsing = iterativelyParseMethodTypes(typeContext, signature, iterativeParsing);
            methodTypes.add(iterativeParsing.result);
        } while (iterativeParsing.more);
        return methodTypes;
    }

    private IterativeParsing<ParameterizedType> iterativelyParseMethodTypes(TypeContext typeContext, String signature, IterativeParsing<ParameterizedType> iterativeParsing) {
        ParameterizedTypeFactory.Result result = ParameterizedTypeFactory.from(typeContext,
                findType, signature.substring(iterativeParsing.startPos));
        int end = iterativeParsing.startPos + result.nextPos;
        IterativeParsing<ParameterizedType> next = new IterativeParsing<>();
        next.result = result.parameterizedType;
        if (end >= signature.length()) {
            next.more = false;
            next.endPos = end;
        } else {
            char atEnd = signature.charAt(end);
            if (atEnd == CARET_THROWS) {
                // FIXME: this marks the "throws" block, which we're NOT parsing at the moment!!
                next.more = false;
                next.endPos = end;
            } else {
                next.more = true;
                if (atEnd == CLOSE_BRACKET) {
                    next.startPos = end + 1;
                } else {
                    next.startPos = end;
                }
            }
        }
        return next;
    }
}
