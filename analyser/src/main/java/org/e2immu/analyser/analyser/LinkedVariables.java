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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.util.SetUtil;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public record LinkedVariables(Set<Variable> variables) {

    public LinkedVariables(Set<Variable> variables) {
        assert variables != null;
        this.variables = Set.copyOf(variables);
    }

    public static final LinkedVariables EMPTY = new LinkedVariables(Set.of());
    public static final LinkedVariables DELAY = new LinkedVariables(Set.of());
    public static final String DELAY_STRING = "<delay>";

    public LinkedVariables merge(LinkedVariables other) {
        if (this == DELAY || other == DELAY) return DELAY;
        if (variables.isEmpty()) return other;
        if (other.variables.isEmpty()) return this;
        return new LinkedVariables(SetUtil.immutableUnion(variables, other.variables));
    }

    public boolean isEmpty() {
        return variables.isEmpty();
    }

    @Override
    public String toString() {
        if (this == EMPTY) return "";
        if (this == DELAY) return DELAY_STRING;

        return variables.stream().map(v -> v.output(Qualification.EMPTY))
                .collect(OutputBuilder.joining(Symbol.COMMA)).debug();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkedVariables that = (LinkedVariables) o;
        return variables.equals(that.variables);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variables);
    }

    public LinkedVariables removeAllButLocalCopiesOf(Variable variable) {
        if (this == DELAY) return DELAY;
        if (this == EMPTY) return EMPTY;
        Set<Variable> remaining = variables.stream().filter(v -> v instanceof LocalVariableReference lvr &&
                variable.equals(lvr.variable.isLocalCopyOf())).collect(Collectors.toSet());
        if (remaining.isEmpty()) return EMPTY;
        return new LinkedVariables(remaining);
    }

    public boolean contains(Variable variable) {
        return variables.contains(variable);
    }
}
