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
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static org.e2immu.analyser.analyser.VariableProperty.INDEPENDENT;
import static org.e2immu.analyser.analyser.VariableProperty.MODIFIED_METHOD;

/*
 can only be created as the single result value of a method

 will be substituted at any time in MethodCall

 Big question: do properties come from the expression, or from the method??
 In the case of Supplier.get() (which is modifying by default), the expression may for example be a parameterized string (t + "abc").
 The string expression is Level 2 immutable, hence not-modified.

 Properties that rely on the return value, should come from the Value. Properties to do with modification, should come from the method.
 */
public record InlinedMethod(MethodInfo methodInfo, Expression expression,
                            Applicability applicability) implements Expression {

    public enum Applicability {
        EVERYWHERE(0), // no references to fields, static or otherwise, unless they are public
        PROTECTED(1), // reference to protected fields
        PACKAGE(2),  // reference to package-private fields
        TYPE(3),   // can only be applied in the same type (reference to private fields)
        METHOD(4), // can only be applied in the same method (reference to local variables)

        NONE(5); // cannot be expressed properly

        public final int order;

        Applicability(int order) {
            this.order = order;
        }

        public Applicability mostRestrictive(Applicability other) {
            return order < other.order ? other : this;
        }
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new InlinedMethod(methodInfo, expression.translate(translationMap), applicability);
    }

    @Override
    public boolean isNumeric() {
        return expression.isNumeric();
    }

    @Override
    public ParameterizedType returnType() {
        return expression.returnType();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        return new OutputBuilder().add(new Text("", "/* inline " + methodInfo.name + " */"))
                .add(expression.output(qualification));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TERNARY;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INLINE_METHOD;
    }

    @Override
    public int internalCompareTo(Expression v) {
        InlinedMethod mv = (InlinedMethod) v;
        return methodInfo.distinguishingName().compareTo(mv.methodInfo.distinguishingName());
    }

    /*
    an inline method has properties on the method, and properties on the expression. these are on the method.
    */
    private final static Set<VariableProperty> METHOD_PROPERTIES_IN_INLINE_SAM = Set.of(MODIFIED_METHOD, INDEPENDENT);

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
        return switch (variableProperty) {
            case MODIFIED_METHOD, INDEPENDENT -> evaluationContext.getAnalyserContext()
                    .getMethodAnalysis(methodInfo).getProperty(variableProperty);
            case NOT_NULL_EXPRESSION -> MultiLevel.EFFECTIVELY_NOT_NULL;
            case IMMUTABLE -> MultiLevel.EFFECTIVELY_E2IMMUTABLE; // a method is immutable
            default -> evaluationContext.getProperty(expression, variableProperty, duringEvaluation, false);
        };
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        return expression.reEvaluate(evaluationContext, translation);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InlinedMethod that = (InlinedMethod) o;
        return methodInfo.equals(that.methodInfo) && expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodInfo, expression);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }

    public boolean canBeApplied(EvaluationContext evaluationContext) {
        return switch (applicability) {
            case EVERYWHERE -> true;
            case NONE -> false;
            case TYPE -> evaluationContext.getCurrentType().primaryType().equals(methodInfo.typeInfo.primaryType());
            case METHOD -> methodInfo.equals(evaluationContext.getCurrentMethod().methodInfo);
            case PACKAGE -> evaluationContext.getCurrentType().packageName().equals(methodInfo.typeInfo.packageName());
            default -> throw new UnsupportedOperationException("TODO");
        };
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationContext) {
        // TODO verify this
        return expression.getInstance(evaluationContext);
    }
}
