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

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analysis.FlowData;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BinaryOperator;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.Precedence;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;


public abstract class SwitchEntry extends StatementWithStructure {

    public final List<Expression> labels;
    public final Expression switchVariableAsExpression;
    protected final Primitives primitives;

    private SwitchEntry(Identifier identifier,
                        Primitives primitives,
                        Structure structure, Expression switchVariableAsExpression, List<Expression> labels) {
        super(identifier, structure);
        this.labels = labels;
        this.switchVariableAsExpression = switchVariableAsExpression;
        this.primitives = primitives;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SwitchEntry that = (SwitchEntry) o;
        return labels.equals(that.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(labels);
    }

    protected void appendLabels(OutputBuilder outputBuilder, Qualification qualification) {
        if (labels.isEmpty()) {
            outputBuilder.add(new Text("default")).add(Symbol.LAMBDA);
        } else {
            outputBuilder
                    .add(labels.stream().map(expression -> expression.output(qualification)).collect(OutputBuilder.joining(Symbol.COMMA)))
                    .add(Symbol.LAMBDA);
        }
    }

    public static Expression generateConditionExpression(
            Primitives primitives,
            List<Expression> labels, Expression switchVariableAsExpression) {
        if (labels.isEmpty()) {
            return EmptyExpression.DEFAULT_EXPRESSION; // this will become the negation of the disjunction of all previous expressions
        }
        MethodInfo operator = operator(primitives, switchVariableAsExpression);
        Expression or = equality(primitives, labels.get(0), switchVariableAsExpression, operator);
        // we group multiple "labels" into one disjunction
        for (int i = 1; i < labels.size(); i++) {
            Expression equality = equality(primitives, labels.get(i), switchVariableAsExpression, operator);
            Identifier id = Identifier.joined("switch condition", List.of(or.getIdentifier(), equality.getIdentifier()));
            or = new BinaryOperator(id, primitives, or, primitives.orOperatorBool(), equality, Precedence.LOGICAL_OR);
        }
        return or;
    }

    private static Expression equality(Primitives primitives, Expression label, Expression switchVariableAsExpression, MethodInfo operator) {
        Identifier id = Identifier.joined("switch entry condition equality",
                List.of(switchVariableAsExpression.getIdentifier(), operator.getIdentifier()));
        return new BinaryOperator(id, primitives, switchVariableAsExpression, operator, label, Precedence.EQUALITY);
    }

    private static MethodInfo operator(Primitives primitives, Expression switchVariableAsExpression) {
        boolean primitive = switchVariableAsExpression.returnType().isPrimitiveExcludingVoid();
        return primitive ? primitives.equalsOperatorInt() : primitives.equalsOperatorObject();
    }

    public static DV statementExecution(List<Expression> labels,
                                        Expression value,
                                        EvaluationResult context) {
        if (labels.isEmpty()) return FlowData.DEFAULT_EXECUTION;
        for (Expression label : labels) {
            EvaluationResult result = label.evaluate(context, ForwardEvaluationInfo.DEFAULT);
            if (result.value().equals(value)) return FlowData.ALWAYS;
        }
        if (value.isConstant()) return FlowData.NEVER;
        return FlowData.CONDITIONALLY;
    }

    //****************************************************************************************************************

    public static class StatementsEntry extends SwitchEntry {

        public StatementsEntry(
                Identifier identifier,
                Primitives primitives,
                Expression switchVariableAsExpression,
                List<Expression> labels,
                List<Statement> statements) {
            super(identifier, primitives, new Structure.Builder()
                    .setExpression(generateConditionExpression(primitives, labels, switchVariableAsExpression))
                    .setStatements(statements == null ? List.of() : statements)
                    .build(), switchVariableAsExpression, labels);
        }

        @Override
        public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
            List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
            if (haveDirectTranslation(direct, this)) return direct;

            Expression tex = switchVariableAsExpression.translate(inspectionProvider, translationMap);
            List<Expression> translatedLabels = labels.stream()
                    .map(l -> l.translate(inspectionProvider, translationMap)).collect(Collectors.toList());
            return List.of(new StatementsEntry(identifier, primitives, tex, translatedLabels,
                    structure.statements().stream()
                            .flatMap(st -> st.translate(inspectionProvider, translationMap)
                                    .stream()).collect(Collectors.toList())));
        }

        @Override
        public OutputBuilder output(Qualification qualification) {
            throw new UnsupportedOperationException(); // need to use a different method!
        }

        @Override
        public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
            OutputBuilder outputBuilder = new OutputBuilder();
            appendLabels(outputBuilder, qualification);

            if (statementAnalysis != null) {
                Guide.GuideGenerator ggStatements = Guide.defaultGuideGenerator();
                outputBuilder.add(ggStatements.start());
                Block.statementsString(qualification, outputBuilder, ggStatements, statementAnalysis);
                outputBuilder.add(ggStatements.end());
            } else if (structure.statements().size() == 1) {
                outputBuilder.add(structure.statements().get(0).output(qualification, null));
            } else {
                outputBuilder.add(structure.statements().stream()
                        .filter(s -> !s.isSynthetic())
                        .map(s -> s.output(qualification, null))
                        .collect(OutputBuilder.joining(Space.NONE, Guide.generatorForBlock())));
            }
            return outputBuilder;
        }

        @Override
        public List<? extends Element> subElements() {
            return ListUtil.immutableConcat(labels, structure.statements());
        }

        @Override
        public void visit(Predicate<Element> predicate) {
            if (predicate.test(this)) {
                labels.forEach(l -> l.visit(predicate));
                structure.statements().forEach(st -> st.visit(predicate));
            }
        }
    }

    //****************************************************************************************************************

    public static class BlockEntry extends SwitchEntry {

        public BlockEntry(Identifier identifier,
                          Primitives primitives,
                          Expression switchVariableAsExpression, List<Expression> labels, Block block) {
            super(identifier, primitives,
                    new Structure.Builder()
                            .setExpression(generateConditionExpression(primitives, labels, switchVariableAsExpression))
                            .setStatementExecution((x, y) -> SwitchEntry.statementExecution(labels, x, y))
                            .setBlock(block)
                            .build(),
                    switchVariableAsExpression, labels);
        }

        @Override
        public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
            List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
            if (haveDirectTranslation(direct, this)) return direct;

            Expression translatedVariable = switchVariableAsExpression.translate(inspectionProvider, translationMap);
            List<Expression> translatedLabels = labels.stream().map(l -> l.translate(inspectionProvider, translationMap))
                    .collect(Collectors.toList());
            List<Statement> translatedBlock = structure.block().translate(inspectionProvider, translationMap);
            return List.of(new BlockEntry(identifier, primitives, translatedVariable, translatedLabels,
                    ensureBlock(structure.block().identifier, translatedBlock)));
        }

        @Override
        public OutputBuilder output(Qualification qualification) {
            throw new UnsupportedOperationException(); // need to use a different method!
        }

        @Override
        public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
            OutputBuilder outputBuilder = new OutputBuilder();
            appendLabels(outputBuilder, qualification);
            outputBuilder.add(structure.block().output(qualification, statementAnalysis));
            return outputBuilder;
        }

        @Override
        public List<? extends Element> subElements() {
            return ListUtil.immutableConcat(labels, List.of(structure.block()));
        }

        @Override
        public void visit(Predicate<Element> predicate) {
            if (predicate.test(this)) {
                labels.forEach(l -> l.visit(predicate));
                structure.block().visit(predicate);
            }
        }
    }
}
