/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.statement;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BinaryOperator;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.Precedence;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.stream.Collectors;


public abstract class SwitchEntry extends StatementWithStructure {

    public final List<Expression> labels;
    public final Expression switchVariableAsExpression;
    protected final Primitives primitives;

    private SwitchEntry(Primitives primitives,
                        Structure structure, Expression switchVariableAsExpression, List<Expression> labels) {
        super(structure);
        this.labels = labels;
        this.switchVariableAsExpression = switchVariableAsExpression;
        this.primitives = primitives;
    }

    protected void appendLabels(OutputBuilder outputBuilder, boolean java12Style, Guide.GuideGenerator guideGenerator) {
        if (labels.isEmpty()) {
            outputBuilder.add(guideGenerator.mid()).add(new Text("default")).add(Symbol.COLON_LABEL);
        } else if (java12Style) {
            outputBuilder.add(guideGenerator.mid())
                    .add(labels.stream().map(Expression::output).collect(OutputBuilder.joining(Symbol.COMMA)))
                    .add(Symbol.LAMBDA);
        } else {
            for (Expression label : labels) {
                outputBuilder.add(guideGenerator.mid())
                        .add(new Text("case"))
                        .add(Space.ONE)
                        .add(label.output())
                        .add(Symbol.COLON_LABEL);
            }
        }
    }

    private static Expression generateConditionExpression(
            Primitives primitives,
            List<Expression> labels, Expression switchVariableAsExpression) {
        if (labels.isEmpty()) {
            return EmptyExpression.DEFAULT_EXPRESSION; // this will become the negation of the disjunction of all previous expressions
        }
        MethodInfo operator = operator(primitives, switchVariableAsExpression);
        Expression or = equality(primitives, labels.get(0), switchVariableAsExpression, operator);
        // we group multiple "labels" into one disjunction
        for (int i = 1; i < labels.size(); i++) {
            or = new BinaryOperator(primitives, or, primitives.orOperatorBool,
                    equality(primitives, labels.get(i), switchVariableAsExpression, operator),
                    Precedence.LOGICAL_OR);
        }
        return or;
    }

    private static Expression equality(Primitives primitives, Expression label, Expression switchVariableAsExpression, MethodInfo operator) {
        return new BinaryOperator(primitives, switchVariableAsExpression, operator, label, Precedence.EQUALITY);
    }

    private static MethodInfo operator(Primitives primitives, Expression switchVariableAsExpression) {
        boolean primitive = Primitives.isPrimitiveExcludingVoid(switchVariableAsExpression.variables().get(0).concreteReturnType());
        return primitive ? primitives.equalsOperatorInt : primitives.equalsOperatorObject;
    }

    abstract OutputBuilder output(Guide.GuideGenerator guideGenerator, StatementAnalysis statementAnalysis);

    public FlowData.Execution statementExecution(Expression value, EvaluationContext evaluationContext) {
        if (switchVariableAsExpression == EmptyExpression.DEFAULT_EXPRESSION) return FlowData.Execution.DEFAULT;
        EvaluationResult result = switchVariableAsExpression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
        return result.value().equals(value) ? FlowData.Execution.ALWAYS : value.isConstant() ? FlowData.Execution.NEVER : FlowData.Execution.CONDITIONALLY;
    }

    //****************************************************************************************************************

    public static class StatementsEntry extends SwitchEntry {
        public final boolean java12Style;

        public StatementsEntry(
                Primitives primitives,
                Expression switchVariableAsExpression,
                boolean java12Style,
                List<Expression> labels,
                List<Statement> statements) {
            super(primitives, new Structure.Builder()
                    .setExpression(generateConditionExpression(primitives, labels, switchVariableAsExpression))
                    .setStatements(statements == null ? List.of() : statements)
                    .build(), switchVariableAsExpression, labels);
            this.java12Style = java12Style;
        }

        @Override
        public Statement translate(TranslationMap translationMap) {
            return new StatementsEntry(primitives, translationMap.translateExpression(switchVariableAsExpression),
                    java12Style,
                    labels.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                    structure.statements.stream()
                            .flatMap(st -> translationMap.translateStatement(st).stream()).collect(Collectors.toList()));
        }

        @Override
        public OutputBuilder output() {
            throw new UnsupportedOperationException(); // need to use a different method!
        }

        @Override
        public OutputBuilder output(StatementAnalysis statementAnalysis) {
            throw new UnsupportedOperationException(); // need to use a different method!
        }

        @Override
        public OutputBuilder output(Guide.GuideGenerator guideGenerator, StatementAnalysis statementAnalysis) {
            OutputBuilder outputBuilder = new OutputBuilder();
            appendLabels(outputBuilder, java12Style, guideGenerator);

            Guide.GuideGenerator ggStatements = Guide.defaultGuideGenerator();
            outputBuilder.add(ggStatements.start());
            Block.statementsString(outputBuilder, ggStatements, statementAnalysis);
            outputBuilder.add(ggStatements.end());

            return outputBuilder;
        }

        @Override
        public List<? extends Element> subElements() {
            return ListUtil.immutableConcat(labels, structure.statements);
        }
    }

    //****************************************************************************************************************

    public static class BlockEntry extends SwitchEntry {

        public BlockEntry(Primitives primitives,
                          Expression switchVariableAsExpression, List<Expression> labels, Block block) {
            super(primitives,
                    new Structure.Builder().setExpression(generateConditionExpression(primitives, labels, switchVariableAsExpression))
                            .setBlock(block).build(), switchVariableAsExpression, labels);
        }

        @Override
        public Statement translate(TranslationMap translationMap) {
            return new BlockEntry(primitives, translationMap.translateExpression(switchVariableAsExpression),
                    labels.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                    translationMap.translateBlock(structure.block));
        }

        @Override
        public OutputBuilder output() {
            throw new UnsupportedOperationException(); // need to use a different method!
        }

        @Override
        public OutputBuilder output(StatementAnalysis statementAnalysis) {
            throw new UnsupportedOperationException(); // need to use a different method!
        }

        @Override
        public OutputBuilder output(Guide.GuideGenerator guideGenerator, StatementAnalysis statementAnalysis) {
            OutputBuilder outputBuilder = new OutputBuilder();
            appendLabels(outputBuilder, true, guideGenerator);
            outputBuilder.add(structure.block.output(statementAnalysis));
            return outputBuilder;
        }

        @Override
        public List<? extends Element> subElements() {
            return ListUtil.immutableConcat(labels, List.of(structure.block));
        }
    }
}
