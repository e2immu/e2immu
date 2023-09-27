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

package org.e2immu.analyser.inspector.impl;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.e2immu.analyser.inspector.AbstractInspectionBuilder;
import org.e2immu.analyser.inspector.AnnotationInspector;
import org.e2immu.analyser.inspector.ExpressionContext;
import org.e2immu.analyser.model.*;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.List;

public class ParameterInspectionImpl extends InspectionImpl implements ParameterInspection {

    public final boolean varArgs;

    private ParameterInspectionImpl(List<AnnotationExpression> annotations, Comment comment, boolean varArgs) {
        super(annotations, Access.PUBLIC, comment, false);
        this.varArgs = varArgs;
    }

    @Override
    public boolean isVarArgs() {
        return varArgs;
    }

    @Container(builds = ParameterInspectionImpl.class)
    public static class Builder extends AbstractInspectionBuilder<ParameterInspection.Builder>
            implements ParameterInspection.Builder, ParameterInspection {
        private final Identifier identifier;
        private boolean varArgs;
        private String name;
        private ParameterizedType parameterizedType;
        private int index = -1;

        public Builder(Identifier identifier) {
            this.identifier = identifier;
        }

        public Builder(Identifier identifier, ParameterizedType parameterizedType, String name, int index) {
            this.name = name;
            this.index = index;
            this.parameterizedType = parameterizedType;
            this.identifier = identifier;
        }

        public Builder setIndex(int index) {
            this.index = index;
            return this;
        }

        public int getIndex() {
            return index;
        }

        public Builder setName(String name) {
            this.name = name;
            return this;
        }

        public String getName() {
            return name;
        }


        public Builder setParameterizedType(ParameterizedType parameterizedType) {
            this.parameterizedType = parameterizedType;
            return this;
        }

        public ParameterizedType getParameterizedType() {
            return parameterizedType;
        }

        @Fluent
        public Builder setVarArgs(boolean varArgs) {
            this.varArgs = varArgs;
            return this;
        }

        @Override
        public boolean isVarArgs() {
            return varArgs;
        }

        @Fluent
        public Builder addAnnotations(@NotNull(content = true) List<AnnotationExpression> annotations) {
            annotations.forEach(super.annotations::add);
            return this;
        }

        @NotModified
        @NotNull
        public ParameterInfo build(MethodInfo owner) {
            assert owner != null : "No owner for parameter " + name;
            assert index >= 0 : "Forgot to set index";
            ParameterInspectionImpl inspection = new ParameterInspectionImpl(getAnnotations(), getComment(), varArgs);
            ParameterInfo parameterInfo = new ParameterInfo(identifier, owner, parameterizedType, name, index);
            parameterInfo.parameterInspection.set(inspection);
            return parameterInfo;
        }

        public void copyAnnotations(Parameter parameter, ExpressionContext expressionContext) {
            for (AnnotationExpr ae : parameter.getAnnotations()) {
                addAnnotation(AnnotationInspector.inspect(expressionContext, ae));
            }
        }
    }
}
