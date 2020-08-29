package org.e2immu.analyser.analyser.methodanalysercomponent;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.CodeOrganization;
import org.e2immu.analyser.model.HasStatements;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

public class CreateNumberedStatements {

    public static NumberedStatement recursivelyCreateNumberedStatements(NumberedStatement parent,
                                                                        @NotNull List<Statement> statements,
                                                                        @NotNull Stack<Integer> indices,
                                                                        @NotNull List<NumberedStatement> numberedStatements) {
        int statementIndex = 0;
        NumberedStatement first = null;
        NumberedStatement previous = null;
        for (Statement statement : statements) {
            NumberedStatement numberedStatement = new NumberedStatement(statement, parent, join(indices, statementIndex));
            numberedStatements.add(numberedStatement);
            if (previous != null) previous.next.set(Optional.of(numberedStatement));
            previous = numberedStatement;
            if (first == null) first = numberedStatement;
            indices.push(statementIndex);

            int blockIndex = 0;
            List<NumberedStatement> blocks = new ArrayList<>();
            CodeOrganization codeOrganization = statement.codeOrganization();
            if (codeOrganization.statements != Block.EMPTY_BLOCK) {
                blockIndex = createBlock(numberedStatement, indices, numberedStatements, blockIndex, blocks, codeOrganization.statements);
            }
            for (CodeOrganization subStatements : codeOrganization.subStatements) {
                if (subStatements.statements != Block.EMPTY_BLOCK) {
                    blockIndex = createBlock(numberedStatement, indices, numberedStatements, blockIndex, blocks, subStatements.statements);
                }
            }
            numberedStatement.blocks.set(ImmutableList.copyOf(blocks));
            indices.pop();

            ++statementIndex;
        }
        if (previous != null)
            previous.next.set(Optional.empty());
        return first;
    }

    private static int createBlock(NumberedStatement parent,
                                   @NotNull Stack<Integer> indices,
                                   List<NumberedStatement> numberedStatements,
                                   int blockIndex,
                                   @NotNull List<NumberedStatement> blocks,
                                   @NotNull HasStatements statements) {
        indices.push(blockIndex);
        NumberedStatement firstOfBlock =
                recursivelyCreateNumberedStatements(parent, statements.getStatements(), indices, numberedStatements);
        blocks.add(firstOfBlock);
        indices.pop();
        return blockIndex + 1;
    }

    @NotModified
    @NotNull
    private static int[] join(@NotNull @NotModified List<Integer> baseIndices, int index) {
        int[] res = new int[baseIndices.size() + 1];
        int i = 0;
        for (Integer bi : baseIndices) res[i++] = bi;
        res[i] = index;
        return res;
    }

}
