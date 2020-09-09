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

package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public interface Element {

    // definition

    default List<? extends Element> subElements() {
        return List.of();
    }

    // search

    default void visit(Consumer<Element> consumer) {
        subElements().forEach(element -> element.visit(consumer));
        consumer.accept(this);
    }

    default <T extends Element> void visit(Consumer<T> consumer, Class<T> clazz) {
        visit(element -> {
            if (clazz.isAssignableFrom(element.getClass())) consumer.accept((T) element);
        });
    }

    default <E extends Element> List<E> collect(Class<E> clazz) {
        List<E> result = new ArrayList<>();
        visit(result::add, clazz);
        return ImmutableList.copyOf(result);
    }

    // translate

    default Element translate(TranslationMap translationMap) {
        return this;
    }

    // side effect

    default SideEffect sideEffect(EvaluationContext evaluationContext) {
        return subElements().stream()
                .map(e -> e.sideEffect(evaluationContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);
    }

    // imports

    default Set<String> imports() {
        return subElements().stream().flatMap(e -> e.imports().stream()).collect(Collectors.toSet());
    }

    // types referenced

    default Set<TypeInfo> typesReferenced() {
        return subElements().stream().flatMap(e -> e.typesReferenced().stream()).collect(Collectors.toSet());
    }

    // variables, in order of appearance

    default List<Variable> variables() {
        return subElements().stream().flatMap(e -> e.variables().stream()).collect(Collectors.toList());
    }
}
