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

package org.e2immu.analyser.model;

import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Bundle;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.List;

public interface Statement extends Element {

    Structure getStructure();

    String label();

    default boolean hasLabel() {
        return label() != null;
    }

    List<Statement> translate(InspectionProvider inspectionProvider, TranslationMap translationMap);

    default boolean haveDirectTranslation(List<Statement> resultOfTranslation, Statement statement) {
        return resultOfTranslation.size() != 1 || resultOfTranslation.get(0) != statement;
    }

    default Block ensureBlock(Identifier identifier, List<Statement> resultOfTranslation) {
        if (resultOfTranslation.size() == 1 && resultOfTranslation.get(0) instanceof Block block) {
            return block;
        }
        if (resultOfTranslation.isEmpty()) return Block.emptyBlock(identifier);
        return new Block.BlockBuilder(identifier).addStatements(resultOfTranslation).build();
    }

    OutputBuilder output(Qualification qualification, LimitedStatementAnalysis statementAnalysis);

    @Override
    default OutputBuilder output(Qualification qualification) {
        throw new UnsupportedOperationException("Use other output method: " + getClass());
    }

    @Override
    default String minimalOutput() {
        return output(Qualification.EMPTY, null).toString();
    }

    default OutputBuilder messageComment(LimitedStatementAnalysis statementAnalysis) {
        if (statementAnalysis != null && statementAnalysis.haveLocalMessages()) {
            OutputBuilder outputBuilder = new OutputBuilder();
            statementAnalysis.localMessageStream().forEach(message -> outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT)
                    .add(new Text(Bundle.INSTANCE.get(message.message().name()))).add(Symbol.RIGHT_BLOCK_COMMENT));
            return outputBuilder;
        }
        return null;
    }

    default boolean isSynthetic() {
        return false;
    }

    default Statement replaceComment(Comment newCommentOrNullToRemove) {
        throw new UnsupportedOperationException("Not yet implemented for " + getClass());
    }

    int getComplexity();
}
