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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.FlowData;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.util.UpgradableBooleanMap;

public class ForEachStatement extends LoopStatement {
    public ForEachStatement(String label,
                            LocalVariable localVariable,
                            Expression expression,
                            Block block) {
        super(new Structure.Builder()
                .setStatementExecution(ForEachStatement::computeExecution)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setLocalVariableCreation(localVariable)
                .setExpression(expression)
                .setBlock(block).build(), label);
    }

    private static FlowData.Execution computeExecution(Expression expression, EvaluationContext evaluationContext) {
        if(expression instanceof ArrayInitializer arrayInitializer && arrayInitializer.multiExpression.expressions().length == 0) {
            return FlowData.Execution.NEVER;
        }
        if(expression instanceof VariableExpression variableExpression) {
            NewObject newObject  = evaluationContext.currentInstance(variableExpression.variable(), evaluationContext.getInitialStatementTime());
            if(newObject != null && !newObject.state.isBoolValueTrue()) {
                // TODO we can try to extract a length or size
            }
        }
        return FlowData.Execution.CONDITIONALLY; // we have no clue
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ForEachStatement(label,
                translationMap.translateLocalVariable(structure.localVariableCreation),
                translationMap.translateExpression(expression),
                translationMap.translateBlock(structure.block));
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(expression.typesReferenced(),
                structure.block.typesReferenced(),
                structure.localVariableCreation.parameterizedType().typesReferenced(true));
    }


    @Override
    public OutputBuilder output(StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if(label != null) {
            outputBuilder.add(new Text(label)).add(Symbol.COLON_LABEL);
        }
        return outputBuilder.add(new Text("for"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(structure.localVariableCreation.parameterizedType().output())
                .add(Space.ONE)
                .add(new Text(structure.localVariableCreation.name()))
                .add(Symbol.COLON)
                .add(structure.expression.output())
                .add(Symbol.RIGHT_PARENTHESIS)
                .add(structure.block.output(StatementAnalysis.startOfBlock(statementAnalysis, 0)));
    }
}
