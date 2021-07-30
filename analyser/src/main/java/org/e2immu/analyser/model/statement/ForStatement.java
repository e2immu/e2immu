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

import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.stream.Collectors;

public class ForStatement extends LoopStatement {

    /**
     * TODO we can go really far here in analysing the initialiser, condition, and updaters.
     * We should. This will provide a better executedAtLeastOnce predicate.
     *
     * @param label     the label of the block
     * @param condition Cannot be null, but can be EmptyExpression
     * @param block     cannot be null, but can be EmptyBlock
     */
    public ForStatement(Identifier identifier,
                        String label,
                        List<Expression> initialisers,
                        Expression condition,
                        List<Expression> updaters,
                        Block block) {
        super(identifier, new Structure.Builder()
                .setStatementExecution(StatementExecution.CONDITIONALLY)
                .setCreateVariablesInsideBlock(true)
                .addInitialisers(initialisers)
                .setExpression(condition)
                .setExpressionIsCondition(true)
                .setUpdaters(updaters)
                .setBlock(block).build(), label);
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        return new ForStatement(identifier, label,
                structure.initialisers().stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                translationMap.translateExpression(expression),
                structure.updaters().stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                translationMap.translateBlock(structure.block()));
    }

    @Override
    public OutputBuilder output(Qualification qualification, StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (label != null) {
            outputBuilder.add(new Text(label)).add(Symbol.COLON_LABEL);
        }
        return outputBuilder.add(new Text("for"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(structure.initialisers().stream().map(expression1 -> expression1.output(qualification)).collect(OutputBuilder.joining(Symbol.COMMA)))
                .add(Symbol.SEMICOLON)
                .add(structure.expression().output(qualification))
                .add(Symbol.SEMICOLON)
                .add(structure.updaters().stream().map(expression2 -> expression2.output(qualification)).collect(OutputBuilder.joining(Symbol.COMMA)))
                .add(Symbol.RIGHT_PARENTHESIS)
                .addIfNotNull(messageComment(statementAnalysis))
                .add(structure.block().output(qualification, StatementAnalysis.startOfBlock(statementAnalysis, 0)));
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(structure.initialisers(),
                List.of(expression),
                structure.updaters(),
                List.of(structure.block()));
    }
}
