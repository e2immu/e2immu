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
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.StringUtil;

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

    protected void appendLabels(StringBuilder sb, int indent, boolean java12Style, boolean newLine) {
        if (labels.isEmpty()) {
            StringUtil.indent(sb, indent);
            sb.append("default:");
            if (newLine) sb.append("\n");
        } else if (java12Style) {
            sb.append(labels.stream().map(l -> l.expressionString(0)).collect(Collectors.joining(", ")));
            sb.append(" ->");
            if (newLine) sb.append("\n");
        } else {
            boolean multiLine = labels.size() > 1 || newLine;
            for (Expression label : labels) {
                StringUtil.indent(sb, indent);
                sb.append("case ");
                sb.append(label.expressionString(0));
                sb.append(":");
                if (multiLine) sb.append("\n");
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
        Expression or = equality(labels.get(0), switchVariableAsExpression, operator);
        // we group multiple "labels" into one disjunction
        for (int i = 1; i < labels.size(); i++) {
            or = new BinaryOperator(or, primitives.orOperatorBool,
                    equality(labels.get(i), switchVariableAsExpression, operator),
                    BinaryOperator.LOGICAL_OR_PRECEDENCE);
        }
        return or;
    }

    private static Expression equality(Expression label, Expression switchVariableAsExpression, MethodInfo operator) {
        return new BinaryOperator(switchVariableAsExpression, operator, label, BinaryOperator.EQUALITY_PRECEDENCE);
    }

    private static MethodInfo operator(Primitives primitives, Expression switchVariableAsExpression) {
        boolean primitive = Primitives.isPrimitiveExcludingVoid(switchVariableAsExpression.variables().get(0).concreteReturnType());
        return primitive ? primitives.equalsOperatorInt : primitives.equalsOperatorObject;
    }

    public boolean isNotDefault() {
        return !labels.isEmpty();
    }

    public FlowData.Execution statementExecution(Value value, EvaluationContext evaluationContext) {
        if (switchVariableAsExpression == EmptyExpression.DEFAULT_EXPRESSION) return FlowData.Execution.DEFAULT;
        EvaluationResult result = switchVariableAsExpression.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
        return result.value.equals(value) ? FlowData.Execution.ALWAYS : value.isConstant() ? FlowData.Execution.NEVER : FlowData.Execution.CONDITIONALLY;
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
        public String statementString(int indent, StatementAnalysis statementAnalysis) {
            StringBuilder sb = new StringBuilder();

            // TODO use the method from Block to catch replacements!

            appendLabels(sb, indent, java12Style, structure.statements.size() > 1);
            if (structure.statements.size() == 1) {
                sb.append(" ");
                sb.append(structure.statements.get(0).statementString(0, statementAnalysis));
            } else {
                for (Statement statement : structure.statements) {
                    sb.append(statement.statementString(indent + 4, statementAnalysis));
                }
            }
            return sb.toString();
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
        public String statementString(int indent, StatementAnalysis statementAnalysis) {
            StringBuilder sb = new StringBuilder();
            appendLabels(sb, indent, true, false);
            sb.append(structure.block.statementString(indent, StatementAnalysis.startOfBlock(statementAnalysis, 0)));
            return sb.toString();
        }

        @Override
        public List<? extends Element> subElements() {
            return ListUtil.immutableConcat(labels, List.of(structure.block));
        }
    }
}
