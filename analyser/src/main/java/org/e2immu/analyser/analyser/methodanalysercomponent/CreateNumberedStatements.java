package org.e2immu.analyser.analyser.methodanalysercomponent;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.NumberedStatement;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.model.Statement;
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
                                                                        @NotNull List<NumberedStatement> numberedStatements,
                                                                        boolean setNextAtEnd) {
        int statementIndex;
        if (setNextAtEnd) {
            statementIndex = 0;
        } else {
            // we're in the replacement mode; replace the existing index value
            statementIndex = indices.pop();
        }
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
            Structure structure = statement.getStructure();
            if (structure.haveStatements()) {
                blockIndex = createBlock(numberedStatement, indices, numberedStatements, blockIndex, blocks, structure.getStatements());
            }
            for (Structure subStatements : structure.subStatements) {
                if (subStatements.haveStatements()) {
                    blockIndex = createBlock(numberedStatement, indices, numberedStatements, blockIndex, blocks, subStatements.getStatements());
                }
            }
            numberedStatement.blocks.set(ImmutableList.copyOf(blocks));
            indices.pop();

            ++statementIndex;
        }
        if (previous != null && setNextAtEnd)
            previous.next.set(Optional.empty());
        return first;
    }

    private static int createBlock(NumberedStatement parent,
                                   @NotNull Stack<Integer> indices,
                                   List<NumberedStatement> numberedStatements,
                                   int blockIndex,
                                   @NotNull List<NumberedStatement> blocks,
                                   @NotNull List<Statement> statements) {
        indices.push(blockIndex);
        NumberedStatement firstOfBlock =
                recursivelyCreateNumberedStatements(parent, statements, indices, numberedStatements, true);
        blocks.add(firstOfBlock);
        indices.pop();
        return blockIndex + 1;
    }

    @NotModified
    @NotNull
    private static List<Integer> join(@NotNull @NotModified List<Integer> baseIndices, int index) {
        List<Integer> newList = new ArrayList<>(baseIndices);
        newList.add(index);
        return newList;
    }

}
