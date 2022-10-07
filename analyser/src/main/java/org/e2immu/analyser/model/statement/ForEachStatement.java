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
import org.e2immu.analyser.model.expression.ArrayInitializer;
import org.e2immu.analyser.model.expression.LocalVariableCreation;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Objects;

public class ForEachStatement extends LoopStatement {
    public final Identifier.PositionalIdentifier positionOfExpression;

    public ForEachStatement(Identifier identifier,
                            String label,
                            LocalVariableCreation localVariableCreation,
                            Expression expression,
                            Identifier.PositionalIdentifier positionOfExpression,
                            Block block,
                            Comment comment) {
        super(identifier, new Structure.Builder()
                .setStatementExecution(ForEachStatement::computeExecution)
                .setForwardEvaluationInfo(ForwardEvaluationInfo.NOT_NULL)
                .addInitialisers(List.of(localVariableCreation))
                .setExpression(expression)
                .setBlock(block)
                .setComment(comment)
                .build(), label);
        this.positionOfExpression = positionOfExpression;
    }

    private static DV computeExecution(Expression expression, EvaluationResult evaluationContext) {
        if (expression.isDelayed()) return expression.causesOfDelay();

        if (expression instanceof ArrayInitializer arrayInitializer) {
            return arrayInitializer.multiExpression.expressions().length == 0 ? FlowData.NEVER : FlowData.ALWAYS;
        }
        return FlowData.CONDITIONALLY; // we have no clue
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj instanceof ForEachStatement other) {
            return identifier.equals(other.identifier)
                    && expression.equals(other.expression)
                    && structure.block().equals(other.structure.block())
                    && structure.initialisers().equals(other.structure.initialisers());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(identifier, expression, structure.block(), structure.initialisers());
    }

    @Override
    public boolean hasExitCondition() {
        return false;
    }

    @Override
    public List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        List<Statement> direct = translationMap.translateStatement(inspectionProvider, this);
        if (haveDirectTranslation(direct, this)) return direct;

        LocalVariableCreation translatedLvc = (LocalVariableCreation) structure.initialisers().get(0)
                .translate(inspectionProvider, translationMap);
        List<Statement> translatedBlock = structure.block().translate(inspectionProvider, translationMap);
        return List.of(new ForEachStatement(identifier, label,
                translatedLvc,
                expression.translate(inspectionProvider, translationMap),
                positionOfExpression,
                ensureBlock(structure.block().identifier, translatedBlock),
                getStructure().comment()));
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(expression.typesReferenced(),
                structure.block().typesReferenced(),
                structure.initialisers().get(0).returnType().typesReferenced(true));
    }


    @Override
    public OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (label != null) {
            outputBuilder.add(new Text(label)).add(Symbol.COLON_LABEL);
        }
        LocalVariableCreation lvc = (LocalVariableCreation) structure.initialisers().get(0);
        LocalVariable lv = lvc.declarations.get(0).localVariable();
        return outputBuilder.add(new Text("for"))
                .add(Symbol.LEFT_PARENTHESIS)
                .add(lvc.isVar ? new OutputBuilder().add(new Text("var")) :
                        lv.parameterizedType().output(qualification))
                .add(Space.ONE)
                .add(new Text(lv.name()))
                .add(Symbol.COLON)
                .add(structure.expression().output(qualification))
                .add(Symbol.RIGHT_PARENTHESIS)
                .addIfNotNull(messageComment(statementAnalysis))
                .add(structure.block().output(qualification, LimitedStatementAnalysis.startOfBlock(statementAnalysis, 0)));
    }
}
