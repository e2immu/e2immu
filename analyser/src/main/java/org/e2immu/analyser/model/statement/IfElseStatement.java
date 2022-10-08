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
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class IfElseStatement extends StatementWithExpression {
    // for convenience, it's also in structure.subStatements.get(0).block
    public final Block elseBlock;

    public IfElseStatement(Identifier identifier,
                           Expression expression,
                           Block ifBlock,
                           Block elseBlock,
                           Comment comment) {
        super(identifier, createCodeOrganization(expression, ifBlock, elseBlock, comment), expression);
        this.elseBlock = elseBlock;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof IfElseStatement other) {
            return identifier.equals(other.identifier)
                    && expression.equals(other.expression)
                    && structure.block().equals(other.structure.block())
                    && elseBlock.equals(other.elseBlock);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, expression, structure.block(), elseBlock);
    }

    // note that we add the expression only once
    private static Structure createCodeOrganization(Expression expression, Block ifBlock, Block elseBlock, Comment comment) {
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
        return builder.setComment(comment).build();
    }

    private static DV standardExecution(Expression v, EvaluationResult evaluationContext) {
        if (v.isDelayed()) return v.causesOfDelay();
        if (v.isBoolValueTrue()) return FlowData.ALWAYS;
        if (v.isBoolValueFalse()) return FlowData.NEVER;
        return FlowData.CONDITIONALLY;
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) return direct;

        List<Statement> translatedIf = structure.block().translate(inspectionProvider, translationMap);
        List<Statement> translatedElse = elseBlock.translate(inspectionProvider, translationMap);
        return List.of(new IfElseStatement(identifier,
                expression.translate(inspectionProvider, translationMap),
                ensureBlock(structure.block().getIdentifier(), translatedIf),
                ensureBlock(elseBlock.identifier, translatedElse), structure.comment()));
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

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
            structure.block().visit(predicate);
            if (!elseBlock.isEmpty()) {
                elseBlock.visit(predicate);
            }
        }
    }
}
