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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.util.SetOnce;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CompanionAnalysisImpl implements CompanionAnalysis {

    private final Expression value;
    private final AnnotationParameters annotationType;
    private final Expression preAspectVariableValue;
    private final List<Expression> parameterValues;

    private CompanionAnalysisImpl(AnnotationParameters annotationType, Expression value, Expression preAspectVariableValue, List<Expression> parameterValues) {
        Objects.requireNonNull(value);
        this.value = value;
        this.annotationType = annotationType;
        this.preAspectVariableValue = preAspectVariableValue;
        this.parameterValues = parameterValues;
    }

    @Override
    public List<Expression> getParameterValues() {
        return parameterValues;
    }

    public Expression getPreAspectVariableValue() {
        return preAspectVariableValue;
    }

    @Override
    public AnnotationParameters getAnnotationType() {
        return annotationType;
    }

    @Override
    public Expression getValue() {
        return value;
    }

    public static class Builder implements CompanionAnalysis {

        private final AnnotationParameters annotationType;
        public final SetOnce<Expression> value = new SetOnce<>();
        public final SetOnce<Map<String, Expression>> remapParameters = new SetOnce<>();
        public final SetOnce<Expression> preAspectVariableValue = new SetOnce<>();
        public final SetOnce<List<Expression>> parameterValues = new SetOnce<>();
        public Builder(AnnotationParameters annotationType) {
            this.annotationType = annotationType;
        }

        public CompanionAnalysis build() {
            return new CompanionAnalysisImpl(annotationType, value.get(), getPreAspectVariableValue(),
                    ImmutableList.copyOf(parameterValues.getOrElse(List.of())));
        }

        @Override
        public List<Expression> getParameterValues() {
            return parameterValues.getOrElse(null);
        }

        @Override
        public Expression getValue() {
            return value.getOrElse(null);
        }

        @Override
        public AnnotationParameters getAnnotationType() {
            return annotationType;
        }

        @Override
        public Expression getPreAspectVariableValue() {
            return preAspectVariableValue.getOrElse(EmptyExpression.NO_VALUE);
        }
    }
}
