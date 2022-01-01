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
import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.FlowData;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

import java.util.List;

public class IfElseStatement extends StatementWithExpression {
    // for convenience, it's also in structure.subStatements.get(0).block
    public final Block elseBlock;

    public IfElseStatement(Identifier identifier,
                           Expression expression,
                           Block ifBlock,
                           Block elseBlock) {
        super(identifier, createCodeOrganization(expression, ifBlock, elseBlock), expression);
        this.elseBlock = elseBlock;
    }

    // note that we add the expression only once
    private static Structure createCodeOrganization(Expression expression, Block ifBlock, Block elseBlock) {
        Structure.Builder builder = new Structure.Builder()
                .setExpression(expression)
                .setExpressionIsCondition(true)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setBlock(ifBlock)
                .setStatementExecution(IfElseStatement::standardExecution);
        if (!elseBlock.isEmpty()) {
            builder.addSubStatement(new Structure.Builder().setExpression(EmptyExpression.DEFAULT_EXPRESSION)
                    .setStatementExecution(StatementExecution.DEFAULT)
                    .setBlock(elseBlock)
                    .build());
        }
        return builder.build();
    }

    private static DV standardExecution(Expression v, EvaluationContext evaluationContext) {
        if (v.isDelayed()) return v.causesOfDelay();
        if (v.isBoolValueTrue()) return FlowData.ALWAYS;
        if (v.isBoolValueFalse()) return FlowData.NEVER;
        return FlowData.CONDITIONALLY;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new IfElseStatement(identifier,
                translationMap.translateExpression(expression),
                translationMap.translateBlock(structure.block()),
                translationMap.translateBlock(elseBlock));
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder().add(new Text("if"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(structure.expression().output(qualification))
                .add(Symbol.RIGHT_PARENTHESIS)
                .addIfNotNull(messageComment(statementAnalysis))
                .add(structure.block().output(qualification, LimitedStatementAnalysis.startOfBlock(statementAnalysis, 0)));
        if (!elseBlock.isEmpty()) {
            outputBuilder.add(new Text("else"))
                    .add(elseBlock.output(qualification, LimitedStatementAnalysis.startOfBlock(statementAnalysis, 1)));
        }
        return outputBuilder;
    }

    @Override
    public List<? extends Element> subElements() {
        if (elseBlock.isEmpty()) {
            return List.of(expression, structure.block());
        }
        return List.of(expression, structure.block(), elseBlock);
    }
}
