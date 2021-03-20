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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public interface Element {

    // definition

    default List<? extends Element> subElements() {
        return List.of();
    }

    // search; do not override

    default void visit(Consumer<Element> consumer) {
        subElements().forEach(element -> element.visit(consumer));
        consumer.accept(this);
    }

    @SuppressWarnings("unchecked")
    default <T extends Element> void visit(Consumer<T> consumer, Class<T> clazz) {
        visit(element -> {
            if (clazz.isAssignableFrom(element.getClass())) consumer.accept((T) element);
        });
    }

    default <E extends Element> List<E> collect(Class<E> clazz) {
        List<E> result = new ArrayList<>();
        visit(result::add, clazz);
        return List.copyOf(result);
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

    // types referenced (used for imports, uploading annotations, dependency tree between types)
    // the boolean distinguishes between an explicit mention (used for import) and an implicit one.
    default UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return subElements().stream().flatMap(e -> e.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());
    }

    // variables, in order of appearance

    default List<Variable> variables() {
        return subElements().stream().flatMap(e -> e.variables().stream()).collect(Collectors.toList());
    }

    OutputBuilder output(Qualification qualification);

    default String minimalOutput() {
        return output(Qualification.EMPTY).toString();
    }

    default String debugOutput() {
        return output(Qualification.EMPTY).debug();
    }

    default TypeInfo definesType() {
        return null;
    }
}
