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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.NotNull;

import java.util.Objects;
import java.util.Set;

@E2Immutable
public record UnevaluatedLambdaExpression(Set<Integer> numberOfParameters,
                                          Boolean nonVoid) implements Expression {
    public UnevaluatedLambdaExpression(Set<Integer> numberOfParameters, Boolean nonVoid) {
        this.numberOfParameters = Set.copyOf(numberOfParameters);
        this.nonVoid = nonVoid;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UnevaluatedLambdaExpression that = (UnevaluatedLambdaExpression) o;
        return numberOfParameters.equals(that.numberOfParameters) &&
                Objects.equals(nonVoid, that.nonVoid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(numberOfParameters, nonVoid);
    }

    // this is NOT a functional interface, merely the return type of the lambda
    @Override
    @NotNull
    public ParameterizedType returnType() {
        throw new UnsupportedOperationException();
    }


    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("", "<unevaluated lambda with " + numberOfParameters + " parameters>"));
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        throw new UnsupportedOperationException();
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return 0;
    }
}
