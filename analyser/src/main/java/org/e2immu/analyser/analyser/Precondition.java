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

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Objects;

/**
 * A precondition is normally an Expression object.
 * However, to facilitate eventual (@Mark, @Only) computations, it is helpful
 * to record the cause of the precondition.
 * <p>
 * This can be a method call (e.g., setOnce.set(value)), with set being a @Mark method) or
 * an assignment.
 * <p>
 * An empty precondition is represented by the boolean constant TRUE.
 */
public record Precondition(Expression expression, List<PreconditionCause> causes) {

    public boolean isDelayed() {
        return expression.isDelayed();
    }

    public CausesOfDelay causesOfDelay() {
        return expression.causesOfDelay();
    }

    public Precondition translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = expression.translate(inspectionProvider, translationMap);
        if (translated != expression) {
            return new Precondition(translated, causes);
        }
        return this;
    }

    public interface PreconditionCause {


    }

    /* ********************************
       support code for CompanionCauses

       code used in AnnotatedAPI of OrgE2ImmuSupport, Freezable$
       the ensure(Not)Frozen methods each have a precondition.
       At the moment this code is very limited, tailored to exactly this situation,
       to be able to use an Annotated API of Freezable in Trie, DependencyGraph
       ********************************/

    public CompanionCause singleCompanionCauseOrNull() {
        if (causes.isEmpty()) return null;
        PreconditionCause pc = causes.get(0);
        return causes.stream().allMatch(c -> c instanceof CompanionCause && c.equals(pc)) ? (CompanionCause) pc : null;
    }

    public record MethodCallAndNegation(MethodCall methodCall, boolean negation) {
    }

    // catches "isFrozen()", "!isFrozen()"
    public MethodCallAndNegation expressionIsPossiblyNegatedMethodCall() {
        Expression e;
        boolean negated;
        if (expression instanceof UnaryOperator ua && ua.isNegation()) {
            e = ua.expression;
            negated = true;
        } else if (expression instanceof Negation negation) {
            e = negation.expression;
            negated = true;
        } else {
            e = expression;
            negated = false;
        }
        if (e instanceof MethodCall methodCall) {
            return new MethodCallAndNegation(methodCall, negated);
        }
        return null;
    }

    // translates the "!isFrozen()" into "@Only(before="frozen"), and checks that fieldInfo == frozen
    public boolean guardsField(AnalyserContext analyserContext, FieldInfo fieldInfo) {
        CompanionCause cc = singleCompanionCauseOrNull();
        if (cc != null) {
            MethodCallAndNegation mc = expressionIsPossiblyNegatedMethodCall();
            if (mc != null) {
                MethodAnalysis analysis = analyserContext.getMethodAnalysis(mc.methodCall.methodInfo);
                MethodAnalysis.Eventual ev = analysis.getEventual();
                return ev.isTestMark() && ev.fields().contains(fieldInfo);
            }
        }
        return false;
    }

    public record CompanionCause(MethodInfo companion) implements PreconditionCause {
        @Override
        public String toString() {
            return "companionMethod:" + companion.name;
        }
    }

      /* **********************************
       end support code for CompanionCauses
       ************************************/

    /**
     * Precondition is inherited from a method with a precondition itself
     */
    public record MethodCallCause(MethodInfo methodInfo, Expression scopeObject) implements PreconditionCause {
        @Override
        public String toString() {
            return "methodCall:" + methodInfo.name;
        }
    }

    /**
     * Precondition is based on an escape such as if(x) throw new IllegalStateException or assert x;
     */
    public static class EscapeCause implements PreconditionCause {
        @Override
        public String toString() {
            return "escape";
        }
    }

    /**
     * Precondition is based on the state caused by a return statement; see Lazy.get()
     */
    public static class StateCause implements PreconditionCause {
        @Override
        public String toString() {
            return "state";
        }
    }

    public Precondition {
        boolean acceptWithoutBoolean = expression.isUnknown() || expression instanceof ContractMark;
        if (!acceptWithoutBoolean && expression.returnType().isNotBooleanOrBoxedBoolean()) {
            throw new UnsupportedOperationException("Need an unknown or boolean value in a precondition, got "
                    + expression + " of type " + expression.returnType());
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

    public Precondition combine(EvaluationResult context, Precondition other) {
        Expression combinedExpression = And.and(context, expression, other.expression);
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
        assert expression.isDelayed();
        return new Precondition(expression, List.of());
    }

    public static Precondition forDelayed(Identifier identifier,
                                          Expression original,
                                          CausesOfDelay causesOfDelay,
                                          Primitives primitives) {
        Expression de = DelayedExpression.forPrecondition(identifier, primitives, original, causesOfDelay);
        return new Precondition(de, List.of());
    }

    public static Precondition noInformationYet(Location location, Primitives primitives) {
        CausesOfDelay causes = DelayFactory.createDelay(location, CauseOfDelay.Cause.NO_PRECONDITION_INFO);
        return forDelayed(location.identifier(), EmptyExpression.EMPTY_EXPRESSION, causes, primitives);
    }

    public boolean isNoInformationYet(MethodInfo currentMethod) {
        return expression instanceof DelayedExpression de
                && de.causesOfDelay().containsCauseOfDelay(CauseOfDelay.Cause.NO_PRECONDITION_INFO,
                c -> c.location().getInfo() == currentMethod);
    }
}
