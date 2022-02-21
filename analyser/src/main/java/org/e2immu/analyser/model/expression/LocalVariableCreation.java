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
import org.e2immu.analyser.model.expression.util.TranslationCollectors;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class LocalVariableCreation extends BaseExpression implements Expression {
    public final List<Declaration> declarations;
    private final Primitives primitives;
    public final boolean isVar;

    public record Declaration(Identifier identifier, LocalVariable localVariable, Expression expression) {
        public LocalVariableReference localVariableReference() {
            return new LocalVariableReference(localVariable, expression);
        }
    }

    public LocalVariableCreation(Primitives primitives, LocalVariable localVariable) {
        this(primitives, List.of(new Declaration(Identifier.generate("lvc"), localVariable,
                EmptyExpression.EMPTY_EXPRESSION)), false);
    }

    public LocalVariableCreation(Primitives primitives, List<Declaration> declarations, boolean isVar) {
        super(declarations.get(0).identifier);
        this.declarations = declarations;
        this.primitives = primitives;
        this.isVar = isVar;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LocalVariableCreation that = (LocalVariableCreation) o;
        return declarations.equals(that.declarations) && isVar == that.isVar;
    }

    @Override
    public int hashCode() {
        return Objects.hash(declarations, isVar);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        List<Declaration> translated = declarations.stream().map(d -> {
            LocalVariable tlv = translationMap.translateLocalVariable(d.localVariable);
            Expression tex = d.expression.translate(translationMap);
            if (tlv == d.localVariable && tex == d.expression) return d;
            return new Declaration(d.identifier, tlv, tex);
        }).collect(TranslationCollectors.toList(declarations));
        if (translated == declarations) return this;
        return new LocalVariableCreation(primitives, translated, isVar);
    }

    @Override
    public int order() {
        return 0;
    }

    @Override
    public ParameterizedType returnType() {
        return primitives.voidParameterizedType();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        LocalVariable lv0 = declarations.get(0).localVariable;
        // annotations
        OutputBuilder outputBuilder = new OutputBuilder()
                .add(lv0.annotations().stream()
                        .map(ae -> ae.output(qualification)).collect(OutputBuilder.joining(Symbol.COMMA)));
        if (!outputBuilder.isEmpty()) {
            outputBuilder.add(Space.ONE);
        }

        // modifiers
        OutputBuilder mods = new OutputBuilder()
                .add(Arrays.stream(LocalVariableModifier.toJava(lv0.modifiers()))
                        .map(s -> new OutputBuilder().add(new Text(s)))
                        .collect(OutputBuilder.joining(Space.ONE)));
        if (!mods.isEmpty()) {
            mods.add(Space.ONE);
        }
        outputBuilder.add(mods);

        // var or type
        if (isVar) {
            outputBuilder.add(new Text("var"));
        } else {
            outputBuilder.add(lv0.parameterizedType().output(qualification));
        }

        // declarations
        outputBuilder.add(Space.ONE);
        outputBuilder.add(declarations.stream().map(declaration -> {
            OutputBuilder ob = new OutputBuilder().add(new Text(declaration.localVariable.name()));
            if (declaration.expression != EmptyExpression.EMPTY_EXPRESSION) {
                ob.add(Symbol.assignment("=")).add(declaration.expression.output(qualification));
            }
            return ob;
        }).collect(OutputBuilder.joining(Symbol.COMMA)));
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
        Stream<Map.Entry<TypeInfo, Boolean>> s1 = declarations.stream()
                .flatMap(d -> d.expression.typesReferenced().stream());
        Stream<Map.Entry<TypeInfo, Boolean>> s2 = declarations.stream()
                .flatMap(d -> d.localVariable.parameterizedType().typesReferenced(true).stream());
        return Stream.concat(s1, s2).collect(UpgradableBooleanMap.collector());
    }

    @Override
    public List<? extends Element> subElements() {
        return declarations.stream().map(Declaration::expression)
                .filter(e -> e != EmptyExpression.EMPTY_EXPRESSION).toList();
    }

    @Override
    public List<LocalVariableReference> newLocalVariables() {
        return declarations.stream().map(Declaration::localVariableReference).toList();
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = null;
        EvaluationResult result = null;
        for (Declaration declaration : declarations) {
            EvaluationResult assigned;
            if (declaration.expression == EmptyExpression.EMPTY_EXPRESSION) {
                assigned = new EvaluationResult.Builder(evaluationContext)
                        .setExpression(declaration.expression)
                        .build();
            } else {
                Assignment assignment = new Assignment(evaluationContext.getPrimitives(),
                        new VariableExpression(declaration.localVariableReference()), declaration.expression);
                assigned = assignment.evaluate(evaluationContext, forwardEvaluationInfo);
            }
            if (result == null) {
                result = assigned;
            } else {
                if (builder == null) {
                    builder = new EvaluationResult.Builder(evaluationContext);
                    builder.compose(result);
                }
                builder.compose(assigned);
            }
        }
        assert builder != null || result != null;
        return builder != null ? builder.build() : result;
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return declarations.stream().map(d -> (Variable) d.localVariableReference()).toList();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            for (Declaration declaration : declarations) {
                declaration.expression.visit(predicate);
            }
        }
    }
}
