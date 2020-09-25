/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

public class BlockAnalysis {

    public final String index;
    public final StatementAnalysis firstStatement;

    public BlockAnalysis(StatementAnalysis parent, List<Statement> statements, Stack<Integer> indices) {
        this.index = indices.stream().map(Object::toString).collect(Collectors.joining("."));
        if (statements.isEmpty()) {
            firstStatement = null;
        } else {
            firstStatement = recursivelyCreateAnalysisObjects(parent, statements, indices, true);
        }
    }


    public static StatementAnalysis recursivelyCreateAnalysisObjects(StatementAnalysis parent,
                                                                     List<Statement> statements,
                                                                     Stack<Integer> indices,
                                                                     boolean setNextAtEnd) {
        int statementIndex;
        if (setNextAtEnd) {
            statementIndex = 0;
        } else {
            // we're in the replacement mode; replace the existing index value
            statementIndex = indices.pop();
        }
        StatementAnalysis first = null;
        StatementAnalysis previous = null;
        for (Statement statement : statements) {
            StatementAnalysis statementAnalysis = new StatementAnalysis(statement, parent, join(indices, statementIndex));
            if (previous != null) previous.next.set(Optional.of(statementAnalysis));
            previous = statementAnalysis;
            if (first == null) first = statementAnalysis;
            indices.push(statementIndex);

            int blockIndex = 0;
            List<BlockAnalysis> blocks = new ArrayList<>();
            Structure structure = statement.getStructure();
            if (structure.haveStatements()) {
                blockIndex = createBlock(statementAnalysis, indices, blockIndex, blocks, structure.getStatements());
            }
            for (Structure subStatements : structure.subStatements) {
                if (subStatements.haveStatements()) {
                    blockIndex = createBlock(statementAnalysis, indices, blockIndex, blocks, subStatements.getStatements());
                }
            }
            statementAnalysis.blocks.set(ImmutableList.copyOf(blocks));
            indices.pop();

            ++statementIndex;
        }
        if (previous != null && setNextAtEnd)
            previous.next.set(Optional.empty());
        return first;

    }

    private static int createBlock(StatementAnalysis parent,
                                   @NotNull Stack<Integer> indices,
                                   int blockIndex,
                                   @NotNull List<BlockAnalysis> blocks,
                                   @NotNull List<Statement> statements) {
        indices.push(blockIndex);
        BlockAnalysis blockAnalysis = new BlockAnalysis(parent, statements, indices);
        blocks.add(blockAnalysis);
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
