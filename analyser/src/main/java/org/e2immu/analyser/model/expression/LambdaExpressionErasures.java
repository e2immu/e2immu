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

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/*
 For a lambda, counts will be {(n, true), (n, false)}, with n the number of parameters
 For a method reference, we'll have one entry per candidate method, where isVoid=false counts as one parameter.
 */
public final class LambdaExpressionErasures extends BaseExpression implements ErasureExpression {
    private final Set<Count> counts;
    private final Location location;

    public LambdaExpressionErasures(Set<Count> counts, Location location) {
        super(Identifier.CONSTANT, 10);
        Objects.requireNonNull(counts);
        Objects.requireNonNull(location);
        this.counts = counts;
        this.location = location;
    }

    public record Count(int parameters, boolean isVoid) {
    }

    // this is NOT a functional interface, merely the return type of the lambda
    @Override
    @NotNull
    public ParameterizedType returnType() {
        throw new UnsupportedOperationException("Location: " + location.detailedLocation());
    }


    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("<" + this + ">"));
    }

    @Override
    public String toString() {
        return "Lambda Erasure at " + location.detailedLocation() + ": " + counts;
    }

    @Override
    public Precedence precedence() {
        return Precedence.BOTTOM;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        throw new UnsupportedOperationException(toString());
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException(toString());
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        throw new UnsupportedOperationException(toString());
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
        throw new ExpressionComparator.InternalError();
    }

    @Override
    public Set<ParameterizedType> erasureTypes(TypeContext typeContext) {
        return counts.stream().map(count -> typeContext.typeMap()
                        .syntheticFunction(count.parameters, count.isVoid).asParameterizedType(typeContext))
                .collect(Collectors.toUnmodifiableSet());
    }

    public Set<Count> counts() {
        return counts;
    }

    public Location location() {
        return location;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (LambdaExpressionErasures) obj;
        return Objects.equals(this.counts, that.counts) &&
                Objects.equals(this.location, that.location);
    }

    @Override
    public int hashCode() {
        return Objects.hash(counts, location);
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visit(Visitor visitor) {
        throw new UnsupportedOperationException();
    }
}
