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

import com.github.javaparser.ast.body.Parameter;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.Modified;

public interface ParameterInspection extends Inspection {

    boolean isVarArgs();

    @Container
    interface Builder extends InspectionBuilder<Builder>, Inspection {

        @Fluent
        Builder setVarArgs(boolean varargs);

        boolean isVarArgs();

        int getIndex();

        @Fluent
        Builder setIndex(int size);

        void copyAnnotations(Parameter parameter, ExpressionContext expressionContext);

        ParameterizedType getParameterizedType();

        ParameterInfo build(MethodInfo methodInfo);

        @Modified
        @Fluent
        Builder setParameterizedType(ParameterizedType parameterizedType);

        @Modified
        @Fluent
        Builder setName(String name);
    }
}
