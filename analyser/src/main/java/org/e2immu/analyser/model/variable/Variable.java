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

package org.e2immu.analyser.model.variable;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.util.OneVariable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.NotNull;

import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * groups: FieldInfo, ParameterInfo, LocalVariable
 */

@ImmutableContainer
public interface Variable extends OneVariable, Comparable<Variable> {

    @Override
    default int compareTo(Variable o) {
        return fullyQualifiedName().compareTo(o.fullyQualifiedName());
    }

    @Override
    default Variable variable() {
        return this;
    }

    static String fullyQualifiedName(Set<Variable> dependencies) {
        if (dependencies == null) return "";
        return dependencies.stream().map(Variable::fullyQualifiedName).collect(Collectors.joining("; "));
    }

    @NotNull
    ParameterizedType parameterizedType();

    /**
     * @return the most simple name that the variable can take. Used to determine which names have already been taken,
     * so that the analyser can introduce a new variable with a unique name.
     */
    @NotNull
    String simpleName();

    @NotNull
    String fullyQualifiedName();

    default String minimalOutput() {
        return simpleName();
    }

    boolean isStatic();

    default UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        return parameterizedType().typesReferenced(explicit);
    }

    default boolean isLocal() {
        return false;
    }

    @NotNull
    OutputBuilder output(Qualification qualification);

    /*
    Used to determine which evaluation context the variable belongs to: the normal one, or a closure?
     */
    default TypeInfo getOwningType() {
        return null;
    }

    default String nameInLinkedAnnotation() {
        return simpleName();
    }

    default VariableNature variableNature() {
        return VariableNature.METHOD_WIDE;
    }

    default int statementTime() {
        if (variable() instanceof FieldReference fr && fr.scope instanceof DelayedVariableExpression dve) {
            return dve.statementTime;
        }
        return VariableInfoContainer.NOT_A_FIELD;
    }

    default void visit(Predicate<Element> predicate) {
        // do nothing, but any variable containing an expression should go there (field reference, dependent variable)
    }

    default boolean hasScopeVariableCreatedAt(String index) {
        return false;
    }

    // causes of delay in scope, index
    default CausesOfDelay causesOfDelay() {
        return CausesOfDelay.EMPTY;
    }

    /*
    scope variable or index variable is contained in set?
     */
    default boolean containsAtLeastOneOf(Set<? extends Variable> variables) { return false; }
}
