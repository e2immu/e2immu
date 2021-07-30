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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Objects;

/**
 * A precondition is normally an Expression object.
 * However to facilitate eventual (@Mark, @Only) computations, it is helpful
 * to record the cause of the precondition.
 * <p>
 * This can be a method call (e.g., setOnce.set(value)), with set being a @Mark method) or
 * an assignment.
 * <p>
 * An empty precondition is represented by the boolean constant TRUE.
 */
public record Precondition(Expression expression, List<PreconditionCause> causes) {

    public interface PreconditionCause {

    }

    /**
     * Precondition is inherited from a method with a precondition itself
     */
    public record MethodCallCause(MethodInfo methodInfo, Expression scopeObject) implements PreconditionCause {
    }

    /**
     * Precondition is based on an escape such as if(x) throw new IllegalStateException or assert x;
     */
    public static class EscapeCause implements PreconditionCause {
    }

    /**
     * Precondition is based on the state caused by a return statement; see Lazy.get()
     */
    public static class StateCause implements PreconditionCause {
    }

    public Precondition {
        if (!expression.isUnknown() && Primitives.isNotBooleanOrBoxedBoolean(expression.returnType())) {
            throw new UnsupportedOperationException("Need an unknown or boolean value in a precondition, got " + expression);
        }
        Objects.requireNonNull(causes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Precondition that = (Precondition) o;
        return expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(expression);
    }

    public boolean isEmpty() {
        return expression.isBoolValueTrue();
    }

    public Precondition combine(EvaluationContext evaluationContext, Precondition other) {
        Expression combinedExpression = And.and(evaluationContext, expression, other.expression);
        return new Precondition(combinedExpression, ListUtil.concatImmutable(causes, other.causes));
    }

    public static Precondition empty(BooleanConstant booleanConstant) {
        assert booleanConstant.constant();
        return new Precondition(booleanConstant, List.of());
    }

    public static Precondition empty(Primitives primitives) {
        return new Precondition(new BooleanConstant(primitives, true), List.of());
    }

    public static Precondition forDelayed(Expression expression) {
        return new Precondition(expression, List.of());
    }
}
