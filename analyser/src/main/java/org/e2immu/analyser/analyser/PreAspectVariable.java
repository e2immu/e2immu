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

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;

/**
 * Purpose: describe the state of the aspect before the modification.
 * Example:
 * <p>
 * add$Modification$Size(int post, int pre, E e) --> post will be mapped to "size()", while "pre" in the
 * expression will describe the "size()" before the modification took place.
 */
public record PreAspectVariable(ParameterizedType returnType,
                                Expression valueForProperties) implements Variable {

    @Override
    public ParameterizedType parameterizedType() {
        return returnType;
    }

    @Override
    public String simpleName() {
        return "pre";
    }

    @Override
    public String fullyQualifiedName() {
        return "pre";
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("pre"));
    }
}
