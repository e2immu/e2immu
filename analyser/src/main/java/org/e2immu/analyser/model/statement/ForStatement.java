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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;

import java.util.List;
import java.util.Objects;
import java.util.Set;
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
                        Block block,
                        Comment comment) {
        super(identifier, new Structure.Builder()
                .setStatementExecution(StatementExecution.CONDITIONALLY)
                .setCreateVariablesInsideBlock(true)
                .addInitialisers(initialisers)
                .setExpression(condition)
                .setExpressionIsCondition(true)
                .setUpdaters(updaters)
                .setBlock(block)
                .setComment(comment)
                .build(), label);
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof IfElseStatement other) {
            return identifier.equals(other.identifier) && subElements().equals(other.subElements());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, subElements());
    }

    // for(int i=0; i<x; i++) has no exit condition: 'i' in 'i<x' is a locally created variable
    // int i; for(i=0; i<x; i++) has one... the variable 'i' in 'i<x' persists after the loop
    @Override
    public boolean hasExitCondition() {
        Set<Variable> locallyCreated = structure.initialisers().stream()
                .filter(i -> i instanceof LocalVariableCreation)
                .flatMap(i -> ((LocalVariableCreation) i).declarations.stream())
                .map(LocalVariableCreation.Declaration::localVariableReference)
                .collect(Collectors.toUnmodifiableSet());
        return structure.expression().variables(true).stream()
                .noneMatch(locallyCreated::contains);
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) return direct;

        Expression tex = expression.translate(inspectionProvider, translationMap);
        List<Statement> translatedBlock = structure.block().translate(inspectionProvider, translationMap);
        List<Expression> updaters = structure.updaters().stream()
                .map(updater -> updater.translate(inspectionProvider, translationMap)).
                collect(Collectors.toList());
        List<Expression> initializers = structure.initialisers().stream()
                .map(init -> init.translate(inspectionProvider, translationMap))
                .collect(Collectors.toList());
        return List.of(new ForStatement(identifier, label, initializers, tex, updaters,
                ensureBlock(structure.block().identifier, translatedBlock), structure.comment()));
    }

    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
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
                .add(structure.block().output(qualification, LimitedStatementAnalysis.startOfBlock(statementAnalysis, 0)));
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(structure.initialisers(),
                List.of(expression),
                structure.updaters(),
                List.of(structure.block()));
    }
}
