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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.StatementAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.output.*;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Block extends StatementWithStructure {
    public static final Block EMPTY_BLOCK = new Block(List.of(), null);
    public final String label;
    public final Structure structure;

    private Block(@NotNull List<Statement> statements, String label) {
        structure = new Structure.Builder()
                .setStatementExecution(StatementExecution.ALWAYS)
                .setStatements(statements).build();
        this.label = label;
    }

    @Container(builds = Block.class)
    public static class BlockBuilder {
        private final List<Statement> statements = new ArrayList<>();
        private String label;

        @Fluent
        public BlockBuilder setLabel(String label) {
            this.label = label;
            return this;
        }

        @Fluent
        public BlockBuilder addStatement(@NotNull Statement statement) {
            this.statements.add(statement);
            return this;
        }

        @NotModified
        @NotNull
        public Block build() {
            if (statements.isEmpty()) return Block.EMPTY_BLOCK;
            // NOTE: we don't do labels on empty blocks. that's pretty useless anyway
            return new Block(new ImmutableList.Builder<Statement>().addAll(statements).build(), label);
        }
    }

    @Override
    public OutputBuilder output(StatementAnalysis statementAnalysis) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (label != null) {
            outputBuilder.add(Space.ONE).add(new Text(label)).add(Symbol.COLON_LABEL);
        }
        outputBuilder.add(Symbol.LEFT_BRACE);
        if (statementAnalysis == null) {
            if (!structure.statements.isEmpty()) {
                Guide.GuideGenerator guideGenerator = new Guide.GuideGenerator();
                outputBuilder.add(guideGenerator.start());
                for (Statement statement : structure.statements) {
                    outputBuilder.add(guideGenerator.mid()).add(statement.output(null));
                }
                outputBuilder.add(guideGenerator.end());
            }
        } else {
            Guide.GuideGenerator guideGenerator = new Guide.GuideGenerator();
            outputBuilder.add(guideGenerator.start());
            statementsString(outputBuilder, guideGenerator, statementAnalysis);
            outputBuilder.add(guideGenerator.end());
        }
        outputBuilder.add(Symbol.RIGHT_BRACE);
        return outputBuilder;
    }

    public static void statementsString(OutputBuilder outputBuilder, Guide.GuideGenerator guideGenerator, StatementAnalysis statementAnalysis) {
        StatementAnalysis sa = statementAnalysis;
        while (sa != null) {
            if (sa.navigationData.replacement.isSet()) {
                outputBuilder.add(guideGenerator.mid())
                        .add(Symbol.LEFT_BLOCK_COMMENT)
                        .add(new Text("code will be replaced"))
                        .add(guideGenerator.mid())
                        .add(sa.statement.output(sa));
                StatementAnalysis moreReplaced = sa.navigationData.next.isSet() ? sa.navigationData.next.get().orElse(null) : null;
                if (moreReplaced != null) {
                    statementsString(outputBuilder, guideGenerator, moreReplaced); // recursion!
                }
                outputBuilder.add(guideGenerator.mid()).add(Symbol.RIGHT_BLOCK_COMMENT);
                sa = sa.navigationData.replacement.get();
            }
            outputBuilder.add(guideGenerator.mid()).add(sa.statement.output(sa));
            sa = sa.navigationData.next.isSet() ? sa.navigationData.next.get().orElse(null) : null;
        }
    }

    public ParameterizedType mostSpecificReturnType(InspectionProvider inspectionProvider) {
        AtomicReference<ParameterizedType> mostSpecific = new AtomicReference<>();
        Primitives primitives = inspectionProvider.getPrimitives();
        visit(statement -> {
            if (statement instanceof ReturnStatement returnStatement) {
                if (returnStatement.expression == EmptyExpression.EMPTY_EXPRESSION) {
                    mostSpecific.set(primitives.voidParameterizedType);
                } else {
                    ParameterizedType returnType = returnStatement.expression.returnType();
                    mostSpecific.set(mostSpecific.get() == null ? returnType : mostSpecific.get().mostSpecific(inspectionProvider, returnType));
                }
            }
        });
        return mostSpecific.get() == null ? primitives.voidParameterizedType : mostSpecific.get();
    }

    @Override
    public List<? extends Element> subElements() {
        return structure.statements;
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        if (this == EMPTY_BLOCK) return this;
        return new Block(structure.statements.stream()
                .flatMap(st -> translationMap.translateStatement(st).stream())
                .collect(Collectors.toList()), label);
    }

    @Override
    public Structure getStructure() {
        return structure;
    }
}
