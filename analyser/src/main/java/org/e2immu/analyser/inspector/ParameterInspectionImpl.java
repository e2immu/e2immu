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

package org.e2immu.analyser.inspector;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.e2immu.analyser.model.*;
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
    public static class Builder extends AbstractInspectionBuilder<Builder> implements ParameterInspection {

        private boolean varArgs;
        private String name;
        private ParameterizedType parameterizedType;
        private int index = -1;

        public Builder() {
        }

        public Builder(ParameterizedType parameterizedType, String name, int index) {
            this.name = name;
            this.index = index;
            this.parameterizedType = parameterizedType;
        }

        public Builder setIndex(int index) {
            this.index = index;
            return this;
        }

        public int getIndex() {
            return index;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setParameterizedType(ParameterizedType parameterizedType) {
            this.parameterizedType = parameterizedType;
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
        public Builder addAnnotations(@NotNull1 List<AnnotationExpression> annotations) {
            annotations.forEach(super.annotations::add);
            return this;
        }

        @NotModified
        @NotNull
        public ParameterInfo build(MethodInfo owner) {
            assert index >= 0 : "Forgot to set index";
            ParameterInspectionImpl inspection = new ParameterInspectionImpl(getAnnotations(), varArgs);
            ParameterInfo parameterInfo = new ParameterInfo(owner, parameterizedType, name, index);
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
