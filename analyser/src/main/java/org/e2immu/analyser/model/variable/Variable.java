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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.Qualification;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.util.OneVariable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * groups: FieldInfo, ParameterInfo, LocalVariable
 */

// at some point: @E2Container
public interface Variable extends OneVariable {

    @Override
    default Variable variable() {
        return this;
    }

    static String fullyQualifiedName(Set<Variable> dependencies) {
        if (dependencies == null) return "";
        return dependencies.stream().map(Variable::fullyQualifiedName).collect(Collectors.joining("; "));
    }

    ParameterizedType concreteReturnType();

    ParameterizedType parameterizedType();

    /**
     * @return the most simple name that the variable can take. Used to determine which names have already been taken,
     * so that the analyser can introduce a new variable with a unique name.
     */
    String simpleName();

    String fullyQualifiedName();

    boolean isStatic();

    SideEffect sideEffect(EvaluationContext evaluationContext);

    default UpgradableBooleanMap<TypeInfo> typesReferenced(boolean explicit) {
        return parameterizedType().typesReferenced(explicit);
    }

    default boolean isLocal() {
        return false;
    }

    OutputBuilder output(Qualification qualification);

    static Variable fake() {
        return new Variable() {
            @Override
            public ParameterizedType concreteReturnType() {
                return ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR;
            }

            @Override
            public ParameterizedType parameterizedType() {
                return ParameterizedType.RETURN_TYPE_OF_CONSTRUCTOR;
            }

            @Override
            public String simpleName() {
                return "fake variable";
            }

            @Override
            public String fullyQualifiedName() {
                return "fake variable";
            }

            @Override
            public boolean isStatic() {
                return false;
            }

            @Override
            public SideEffect sideEffect(EvaluationContext evaluationContext) {
                return null;
            }

            @Override
            public OutputBuilder output(Qualification qualification) {
                return new OutputBuilder().add(new Text("fake variable"));
            }

        };
    }

    /*
    Used to determine which evaluation context the variable belongs to: the normal one, or a closure?
     */
    default TypeInfo getOwningType() {
        return null;
    }

    default String nameInLinkedAnnotation() {
        return simpleName();
    }

    default boolean needsNewVariableWithoutValueCall() {
        return false;
    }
}
