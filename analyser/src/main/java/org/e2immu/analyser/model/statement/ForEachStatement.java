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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.FlowData;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;

public class ForEachStatement extends LoopStatement {
    public ForEachStatement(String label,
                            LocalVariableCreation localVariableCreation,
                            Expression expression,
                            Block block) {
        super(new Structure.Builder()
                .setStatementExecution(ForEachStatement::computeExecution)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .addInitialisers(List.of(localVariableCreation))
                .setExpression(expression)
                .setBlock(block).build(), label);
    }

    private static FlowData.Execution computeExecution(Expression expression, EvaluationContext evaluationContext) {
        if (evaluationContext.isDelayed(expression)) return FlowData.Execution.DELAYED_EXECUTION;

        if (expression instanceof ArrayInitializer arrayInitializer) {
            return arrayInitializer.multiExpression.expressions().length == 0 ? FlowData.Execution.NEVER : FlowData.Execution.ALWAYS;
        }
        /* IMPROVE we can try to extract a length or size
        if (expression instanceof VariableExpression variableExpression) {
            NewObject newObject = evaluationContext.currentInstance(variableExpression.variable(), evaluationContext.getInitialStatementTime());
            if (newObject != null && !newObject.state().isBoolValueTrue()) { ... }
        }
        */
        return FlowData.Execution.CONDITIONALLY; // we have no clue
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ForEachStatement(label,
                (LocalVariableCreation) translationMap.translateExpression(structure.initialisers().get(0)),
                translationMap.translateExpression(expression),
                translationMap.translateBlock(structure.block()));
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(expression.typesReferenced(),
                structure.block().typesReferenced(),
                structure.initialisers().get(0).returnType().typesReferenced(true));
    }


    @Override
    public OutputBuilder output(Qualification qualification, StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (label != null) {
            outputBuilder.add(new Text(label)).add(Symbol.COLON_LABEL);
        }
        LocalVariableCreation lvc = (LocalVariableCreation) structure.initialisers().get(0);
        return outputBuilder.add(new Text("for"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(lvc.localVariable.parameterizedType().output(qualification))
                .add(Space.ONE)
                .add(new Text(lvc.localVariable.name()))
                .add(Symbol.COLON)
                .add(structure.expression().output(qualification))
                .add(Symbol.RIGHT_PARENTHESIS)
                .addIfNotNull(messageComment(statementAnalysis))
                .add(structure.block().output(qualification, StatementAnalysis.startOfBlock(statementAnalysis, 0)));
    }
}
