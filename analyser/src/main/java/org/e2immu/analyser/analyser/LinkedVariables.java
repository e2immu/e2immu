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

package org.e2immu.analyser.analyser;

import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.util.SetUtil;

import java.util.Set;
import java.util.stream.Collectors;

public record LinkedVariables(Set<Variable> variables) {

    public LinkedVariables(Set<Variable> variables) {
        assert variables != null;
        this.variables = ImmutableSet.copyOf(variables);
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
