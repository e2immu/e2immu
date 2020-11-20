/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.e2immu.analyser.parser.ExpressionContext;
import org.e2immu.annotation.*;

import java.util.List;

public class ParameterInspectionImpl extends InspectionImpl implements ParameterInspection {

    public final boolean varArgs;

    private ParameterInspectionImpl(List<AnnotationExpression> annotations, boolean varArgs) {
        super(annotations);
        this.varArgs = varArgs;
    }

    @Override
    public boolean isVarArgs() {
        return varArgs;
    }

    @Container(builds = ParameterInspectionImpl.class)
    public static class Builder extends AbstractInspectionBuilder implements BuilderWithAnnotations<Builder>, ParameterInspection {

        private boolean varArgs;

        @Fluent
        public Builder setVarArgs(boolean varArgs) {
            this.varArgs = varArgs;
            return this;
        }

        @Override
        public boolean isVarArgs() {
            return varArgs;
        }

        @Override
        @Fluent
        public Builder addAnnotation(@NotNull AnnotationExpression annotationExpression) {
            annotations.add(annotationExpression);
            return this;
        }

        @Fluent
        public Builder addAnnotations(@NotNull1 List<AnnotationExpression> annotations) {
            annotations.forEach(super.annotations::add);
            return this;
        }

        @NotModified
        @NotNull
        public ParameterInspectionImpl build() {
            return new ParameterInspectionImpl(getAnnotations(), varArgs);
        }

        public void inspect(Parameter parameter, ExpressionContext expressionContext, boolean varArgs) {
            for (AnnotationExpr ae : parameter.getAnnotations()) {
                addAnnotation(AnnotationExpressionImpl.inspect(expressionContext, ae));
            }
            setVarArgs(varArgs);
        }
    }
}
