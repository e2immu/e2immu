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

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.output.Keyword;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Text;

import java.util.Objects;

public class ReturnVariable implements Variable {
    public final ParameterizedType returnType;
    public final String simpleName;
    public final String fqn;
    private final MethodInfo methodInfo;

    public ReturnVariable(MethodInfo methodInfo) {
        this.returnType = methodInfo.returnType();
        simpleName = methodInfo.name;
        fqn = methodInfo.fullyQualifiedName();
        this.methodInfo = methodInfo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReturnVariable that = (ReturnVariable) o;
        return returnType.equals(that.returnType) &&
                fqn.equals(that.fqn);
    }

    @Override
    public TypeInfo getOwningType() {
        return methodInfo.typeInfo;
    }

    @Override
    public int hashCode() {
        return Objects.hash(returnType, fqn);
    }

    @Override
    public ParameterizedType parameterizedType() {
        return returnType;
    }

    @Override
    public String simpleName() {
        return "return " + simpleName;
    }

    @Override
    public String fullyQualifiedName() {
        return fqn;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(Keyword.RETURN).add(Space.ONE).add(new Text(simpleName));
    }

    @Override
    public String toString() {
        return simpleName;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    @Override
    public int getComplexity() {
        return 1;
    }
}
