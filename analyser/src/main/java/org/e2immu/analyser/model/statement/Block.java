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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.StringUtil;
import org.e2immu.annotation.Container;
import org.e2immu.annotation.Fluent;
import org.e2immu.annotation.NotModified;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class Block extends StatementWithStructure {
    public static final Block EMPTY_BLOCK = new Block(List.of(), null);
    public final String label;
    public final Structure structure;

    private Block(@NotNull List<Statement> statements, String label) {
        structure = new Structure.Builder()
                .setStatementsExecutedAtLeastOnce(v -> true)
                .setNoBlockMayBeExecuted(false)
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
    public String statementString(int indent, StatementAnalysis statementAnalysis) {
        StringBuilder sb = new StringBuilder();
        if (label != null) {
            sb.append(label);
            sb.append(":");
        }
        sb.append(" {");
        if (statementAnalysis == null) {
            if (structure.statements.isEmpty()) {
                sb.append(" }\n");
            } else {
                sb.append("\n");
                for (Statement statement : structure.statements) {
                    sb.append(statement.statementString(indent + 4, null));
                }
                StringUtil.indent(sb, indent);
                sb.append("}");
            }
        } else {
            sb.append("\n");
            sb.append(statementsString(indent + 4, statementAnalysis));
            StringUtil.indent(sb, indent);
            sb.append("}");
        }
        return sb.toString();
    }

    private String statementsString(int indent, StatementAnalysis statementAnalysis) {
        StatementAnalysis sa = statementAnalysis;
        StringBuilder sb = new StringBuilder();
        while (sa != null) {
            if (sa.navigationData.replacement.isSet()) {
                StringUtil.indent(sb, indent);
                sb.append("/* code will be replaced\n");
                sb.append(sa.statement.statementString(indent, sa));
                StatementAnalysis moreReplaced = sa.navigationData.next.isSet() ? sa.navigationData.next.get().orElse(null) : null;
                if (moreReplaced != null) {
                    sb.append(statementsString(indent, moreReplaced));
                }
                StringUtil.indent(sb, indent);
                sb.append("*/\n");
                sa = sa.navigationData.replacement.get();
            }
            sb.append(sa.statement.statementString(indent, sa));
            sa = sa.navigationData.next.isSet() ? sa.navigationData.next.get().orElse(null) : null;
        }
        return sb.toString();
    }

    public ParameterizedType mostSpecificReturnType() {
        AtomicReference<ParameterizedType> mostSpecific = new AtomicReference<>();
        visit(statement -> {
            if (statement instanceof ReturnStatement) {
                ReturnStatement returnStatement = (ReturnStatement) statement;
                if (returnStatement.expression == EmptyExpression.EMPTY_EXPRESSION) {
                    mostSpecific.set(Primitives.PRIMITIVES.voidParameterizedType);
                } else {
                    ParameterizedType returnType = returnStatement.expression.returnType();
                    mostSpecific.set(mostSpecific.get() == null ? returnType : mostSpecific.get().mostSpecific(returnType));
                }
            }
        });
        return mostSpecific.get() == null ? Primitives.PRIMITIVES.voidParameterizedType : mostSpecific.get();
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
}
