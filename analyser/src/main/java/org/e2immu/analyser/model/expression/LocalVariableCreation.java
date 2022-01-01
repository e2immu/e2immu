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
import org.e2immu.analyser.model.impl.ElementImpl;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class LocalVariableCreation extends ElementImpl implements Expression {

    public final LocalVariable localVariable;
    public final LocalVariableReference localVariableReference;
    public final Expression expression;
    private final InspectionProvider inspectionProvider;
    public final boolean isVar;

    public LocalVariableCreation(
            @NotNull InspectionProvider inspectionProvider,
            @NotNull LocalVariable localVariable) {
        this(Identifier.generate(), inspectionProvider, localVariable, EmptyExpression.EMPTY_EXPRESSION, false);
    }

    public LocalVariableCreation(
            Identifier identifier,
            @NotNull InspectionProvider inspectionProvider,
            @NotNull LocalVariable localVariable,
            @NotNull Expression expression,
            boolean isVar) {
        super(identifier);
        this.localVariable = Objects.requireNonNull(localVariable);
        this.expression = Objects.requireNonNull(expression);
        this.inspectionProvider = inspectionProvider;
        localVariableReference = new LocalVariableReference(localVariable, expression);
        this.isVar = isVar;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalVariableCreation that = (LocalVariableCreation) o;
        return localVariable.equals(that.localVariable) &&
                expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(localVariable, expression);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new LocalVariableCreation(identifier,
                inspectionProvider, translationMap.translateLocalVariable(localVariable),
                translationMap.translateExpression(expression), isVar);
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public ParameterizedType returnType() {
        return inspectionProvider.getPrimitives().voidParameterizedType();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder outputBuilder = new OutputBuilder()
                .add(localVariable.annotations().stream()
                        .map(ae -> ae.output(qualification)).collect(OutputBuilder.joining(Symbol.COMMA)));
        if (!outputBuilder.isEmpty()) {
            outputBuilder.add(Space.ONE);
        }
        OutputBuilder mods = new OutputBuilder()
                .add(Arrays.stream(LocalVariableModifier.toJava(localVariable.modifiers()))
                        .map(s -> new OutputBuilder().add(new Text(s)))
                        .collect(OutputBuilder.joining(Space.ONE)));
        if (!mods.isEmpty()) {
            mods.add(Space.ONE);
        }
        outputBuilder.add(mods);
        if (isVar) {
            outputBuilder.add(new Text("var"));
        } else {
            outputBuilder.add(localVariable.parameterizedType().output(qualification));
        }
        outputBuilder.add(Space.ONE).add(new Text(localVariable.name()));
        if (expression != EmptyExpression.EMPTY_EXPRESSION) {
            outputBuilder.add(Symbol.assignment("=")).add(expression.output(qualification));
        }
        return outputBuilder;
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
        return UpgradableBooleanMap.of(
                expression.typesReferenced(),
                localVariable.parameterizedType().typesReferenced(true));
    }

    @Override
    public List<? extends Element> subElements() {
        if (expression == EmptyExpression.EMPTY_EXPRESSION) return List.of();
        return List.of(expression);
    }

    @Override
    public List<LocalVariableReference> newLocalVariables() {
        return List.of(localVariableReference);
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (expression == EmptyExpression.EMPTY_EXPRESSION) {
            return new EvaluationResult.Builder(evaluationContext)
                    .setExpression(expression)
                    .build();
        }
        Assignment assignment = new Assignment(evaluationContext.getPrimitives(),
                new VariableExpression(localVariableReference), expression);
        return assignment.evaluate(evaluationContext, forwardEvaluationInfo);
    }

    @Override
    public List<Variable> variables() {
        return List.of(localVariableReference);
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }
}
