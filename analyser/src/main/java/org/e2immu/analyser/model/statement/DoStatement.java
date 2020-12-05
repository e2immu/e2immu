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

import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.StatementExecution;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;

public class DoStatement extends LoopStatement {

    public DoStatement(String label,
                       Expression expression,
                       Block block) {
        super(new Structure.Builder()
                .setStatementExecution(StatementExecution.ALWAYS)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .setExpression(expression)
                .setExpressionIsCondition(true)
                .setBlock(block).build(), label);
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new DoStatement(label, translationMap.translateExpression(expression),
                translationMap.translateBlock(structure.block));
    }


    @Override
    public OutputBuilder output(StatementAnalysis statementAnalysis) {
        return new OutputBuilder().add(new Text("do"))
                .add(structure.block.output(StatementAnalysis.startOfBlock(statementAnalysis, 0)))
                .add(Space.EASY)
                .add(new Text("while"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(structure.expression.output())
                .add(Symbol.RIGHT_PARENTHESIS);
    }
}
