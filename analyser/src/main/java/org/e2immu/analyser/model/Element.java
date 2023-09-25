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

import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.analyser.util.UpgradableIntMap;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface Element {

    @NotNull
    Identifier getIdentifier();

    // definition

    @NotNull(content = true)
    default List<? extends Element> subElements() {
        return List.of();
    }

    // search; do not override

    default void visit(Consumer<Element> consumer) {
        subElements().forEach(element -> element.visit(consumer));
        consumer.accept(this);
    }

    /**
     * Tests the value first, and only if true, visit deeper.
     *
     * @param predicate return true if the search is to be continued deeper
     */
    void visit(Predicate<Element> predicate);

    @SuppressWarnings("unchecked")
    default <T extends Element> void visit(Consumer<T> consumer, Class<T> clazz) {
        visit(element -> {
            if (clazz.isAssignableFrom(element.getClass())) consumer.accept((T) element);
        });
    }

    @NotNull(content = true)
    default <E extends Element> List<E> collect(Class<E> clazz) {
        List<E> result = new ArrayList<>();
        visit(result::add, clazz);
        return List.copyOf(result);
    }

    // types referenced (used for imports, uploading annotations, dependency tree between types)
    // the boolean distinguishes between an explicit mention (used for import) and an implicit one.
    @NotNull
    default UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return subElements().stream().flatMap(e -> e.typesReferenced().stream()).collect(UpgradableBooleanMap.collector());
    }

    @NotNull
    default UpgradableIntMap<TypeInfo> typesReferenced2(int weight) {
        return subElements().stream().flatMap(e -> e.typesReferenced2(weight).stream()).collect(UpgradableIntMap.collector());
    }

    enum DescendMode {
        NO,
        YES,
        YES_INCLUDE_THIS
    }

    @Deprecated
    default List<Variable> variables(boolean b) {
        return b ? variables(DescendMode.YES): variables(DescendMode.NO);
    }

    default List<Variable> variables() {
        // the e2immu default is to descend, but to exclude This
        return variables(DescendMode.YES);
    }
    // can be made more efficient in implementations
    default Stream<Variable> variableStream() {
        return variables(DescendMode.YES).stream();
    }

    // variables, in order of appearance
    @NotNull(content = true)
    default List<Variable> variables(DescendMode descendMode) {
        return subElements().stream()
                .flatMap(e -> e.variables(descendMode).stream())
                .collect(Collectors.toList());
    }

    @NotNull
    OutputBuilder output(Qualification qualification);

    @NotNull
    default String minimalOutput() {
        return output(Qualification.EMPTY).toString();
    }

    default TypeInfo definesType() {
        return null;
    }

    default boolean isInstanceOf(Class<? extends Expression> clazz) {
        return clazz.isAssignableFrom(getClass());
    }

    @SuppressWarnings("unchecked")
    default <T extends Element> T asInstanceOf(Class<T> clazz) {
        if (clazz.isAssignableFrom(getClass())) {
            return (T) this;
        }
        return null;
    }
}
