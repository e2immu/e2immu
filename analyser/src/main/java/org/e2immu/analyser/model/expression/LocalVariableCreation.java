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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.TranslationCollectors;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;

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

        // used in JFocus
        public Declaration changeName(String newName) {
            LocalVariable lv = new LocalVariable(localVariable.modifiers(), newName, localVariable.parameterizedType(),
                    localVariable.annotations(), localVariable.owningType(), localVariable.nature());
            return new Declaration(identifier, lv, expression);
        }

        public Declaration translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
            LocalVariable tlv = translationMap.translateLocalVariable(localVariable);
            Expression tex = expression.translate(inspectionProvider, translationMap);
            if (tlv == localVariable && tex == expression) return this;
            if (localVariable.parameterizedType().isBoxedExcludingVoid() && expression.isNull()
                    && tlv.parameterizedType().isPrimitiveExcludingVoid() && tex.isNull()) {
                // special case: Integer v = null, with type change to int v = null; ... change null to 0
                Expression nullValue = ConstantExpression.nullValue(inspectionProvider.getPrimitives(),
                        tlv.parameterizedType().typeInfo);
                return new Declaration(identifier, tlv, nullValue);
            }
            return new Declaration(identifier, tlv, tex);
        }
    }

    public LocalVariableCreation(Identifier identifier, Primitives primitives, LocalVariable localVariable) {
        this(primitives, List.of(new Declaration(identifier, localVariable,
                EmptyExpression.EMPTY_EXPRESSION)), false);
    }

    public LocalVariableCreation(Primitives primitives, List<Declaration> declarations, boolean isVar) {
        super(declarations.get(0).identifier,
                declarations.stream().mapToInt(d -> d.expression.getComplexity()).sum());
        this.declarations = declarations;
        this.primitives = primitives;
        this.isVar = isVar;
    }

    /*
    convenience factory method
     */
    public static LocalVariableCreation of(Identifier identifier,
                                           Primitives primitives,
                                           String name,
                                           ParameterizedType type,
                                           Expression expression) {
        LocalVariable localVariable = new LocalVariable(name, type);
        Declaration declaration = new Declaration(identifier, localVariable, expression);
        return new LocalVariableCreation(primitives, List.of(declaration), false);
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
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = translationMap.translateExpression(this);
        if (translated != this) return translated;

        List<Declaration> translatedDeclarations = declarations.stream()
                .map(d -> d.translate(inspectionProvider, translationMap))
                .collect(TranslationCollectors.toList(declarations));
        if (translatedDeclarations == declarations) return this;
        return new LocalVariableCreation(primitives, translatedDeclarations, isVar);
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
                .add(lv0.modifiers().stream()
                        .map(s -> new OutputBuilder().add(s.keyword))
                        .collect(OutputBuilder.joining(Space.ONE)));
        if (!mods.isEmpty()) {
            mods.add(Space.ONE);
        }
        outputBuilder.add(mods);

        // var or type
        if (isVar) {
            outputBuilder.add(Keyword.VAR);
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
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (forwardEvaluationInfo.isOnlySort()) {
            List<Declaration> evaluatedDeclarations = declarations.stream()
                    .map(d -> d.expression == null ? d :
                            new Declaration(d.identifier, d.localVariable,
                                    d.expression.evaluate(context, forwardEvaluationInfo).getExpression()))
                    .toList();
            LocalVariableCreation lvc = new LocalVariableCreation(primitives, evaluatedDeclarations, isVar);
            return new EvaluationResult.Builder(context).setExpression(lvc).build();
        }
        EvaluationResult.Builder builder = null;
        EvaluationResult result = null;
        for (Declaration declaration : declarations) {
            EvaluationResult assigned;
            if (declaration.expression == EmptyExpression.EMPTY_EXPRESSION) {
                assigned = new EvaluationResult.Builder(context)
                        .setExpression(declaration.expression)
                        .build();
            } else {
                Assignment assignment = new Assignment(context.getPrimitives(),
                        new VariableExpression(declaration.localVariableReference()), declaration.expression);
                assigned = assignment.evaluate(context, forwardEvaluationInfo);
            }
            if (result == null) {
                result = assigned;
            } else {
                if (builder == null) {
                    builder = new EvaluationResult.Builder(context);
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
        return declarations.stream()
                .flatMap(d -> Stream.concat(Stream.of((Variable) d.localVariableReference()),
                        d.expression.variables(descendIntoFieldReferences).stream()))
                .toList();
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            for (Declaration declaration : declarations) {
                declaration.expression.visit(predicate);
            }
        }
    }
}
