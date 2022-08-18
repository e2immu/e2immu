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

package org.e2immu.analyser.parser.modification.testexample;

import org.e2immu.annotation.*;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Almost identical to SimpleNotModified1, but now there's a "barrier" of <code>requireNonNull</code>
 * between <code>input</code> and <code>set</code>. The method introduces a
 * {@link org.e2immu.analyser.model.expression.PropertyWrapper}
 * around the {@link org.e2immu.analyser.model.expression.VariableExpression}, which forces us to use
 * <code>Value.asInstanceOf</code> rather than the <code>instanceof</code> operator.
 * <p>
 * At the same time <code>set</code> has been made explicitly final, reducing complexity.
 * <p>
 * For <code>input</code> to be marked <code>@Modified</code>, xx
 * has to link an already known to be <code>@Modified</code> field <code>set</code> to the parameter.
 */
@FinalFields
@Container(absent = true)
public class Modification_8 {

    @Immutable(absent = true)
    @NotNull
    @Modified
    private final Set<String> set;

    public Modification_8(@NotNull @Modified Set<String> input) {
        set = Objects.requireNonNull(input);
    }

    @NotModified
    public Stream<String> stream() {
        return set.stream();
    }

    @NotModified
    public Set<String> getSet() {
        return set;
    }

    @Modified
    public void add(String s) {
        set.add(s);
    }
}
