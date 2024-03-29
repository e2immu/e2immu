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

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.annotation.NotNull;

import java.util.Objects;

public abstract class ExpressionWithMethodReferenceResolution extends BaseExpression implements Expression {

    public final MethodInfo methodInfo;
    public final ParameterizedType concreteReturnType;

    protected ExpressionWithMethodReferenceResolution(Identifier identifier,
                                                      int complexity,
                                                      @NotNull MethodInfo methodInfo,
                                                      @NotNull ParameterizedType concreteReturnType) {
        super(identifier, complexity);
        this.concreteReturnType = Objects.requireNonNull(concreteReturnType);
        this.methodInfo = Objects.requireNonNull(methodInfo);
    }

    @Override
    public ParameterizedType returnType() {
        return concreteReturnType;
    }

}
