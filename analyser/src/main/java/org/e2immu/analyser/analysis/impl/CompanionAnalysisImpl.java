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

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.analyser.CompanionAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.support.SetOnce;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CompanionAnalysisImpl implements CompanionAnalysis {

    private final Expression value;
    private final AnnotationParameters annotationType;
    private final Expression preAspectVariableValue;
    private final List<Expression> parameterValues;

    private CompanionAnalysisImpl(AnnotationParameters annotationType,
                                  Expression value,
                                  Expression preAspectVariableValue,
                                  List<Expression> parameterValues) {
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
                    List.copyOf(parameterValues.getOrDefault(List.of())));
        }

        @Override
        public List<Expression> getParameterValues() {
            return parameterValues.getOrDefaultNull();
        }

        @Override
        public Expression getValue() {
            return value.getOrDefaultNull();
        }

        @Override
        public AnnotationParameters getAnnotationType() {
            return annotationType;
        }

        @Override
        public Expression getPreAspectVariableValue() {
            return preAspectVariableValue.getOrDefaultNull();
        }
    }
}
