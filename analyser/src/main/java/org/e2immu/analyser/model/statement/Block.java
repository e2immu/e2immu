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
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.NumberedStatement;
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
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class Block implements Statement, HasStatements {
    public static final Block EMPTY_BLOCK = new Block(List.of(), null);
    public final String label;

    @NotNull
    public final List<Statement> statements;

    private Block(@NotNull List<Statement> statements, String label) {
        this.label = label;
        this.statements = statements;
    }

    public Set<TypeInfo> typesReferenced() {
        Set<TypeInfo> types = new HashSet<>();
        for (Statement statement : statements) {
            types.addAll(statement.typesReferenced());
        }
        return types;
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
    public String statementString(int indent, NumberedStatement numberedStatement) {
        StringBuilder sb = new StringBuilder();
        if (label != null) {
            sb.append(label);
            sb.append(":");
        }
        sb.append(" {");
        if (numberedStatement == null) {
            if (statements.isEmpty()) {
                sb.append(" }\n");
            } else {
                sb.append("\n");
                for (Statement statement : statements) {
                    sb.append(statement.statementString(indent + 4, null));
                }
                StringUtil.indent(sb, indent);
                sb.append("}");
            }
        } else {
            sb.append("\n");
            sb.append(statementsString(indent + 4, numberedStatement));
            StringUtil.indent(sb, indent);
            sb.append("}");
        }
        return sb.toString();
    }

    private String statementsString(int indent, NumberedStatement numberedStatement) {
        NumberedStatement ns = numberedStatement;
        StringBuilder sb = new StringBuilder();
        while (ns != null) {
            if (ns.replacement.isSet()) {
                StringUtil.indent(sb, indent);
                sb.append("/* code will be replaced\n");
                sb.append(ns.statement.statementString(indent, ns));
                NumberedStatement moreReplaced = ns.next.isSet() ? ns.next.get().orElse(null) : null;
                if (moreReplaced != null) {
                    sb.append(statementsString(indent, moreReplaced));
                }
                StringUtil.indent(sb, indent);
                sb.append("*/\n");
                ns = ns.replacement.get();
            }
            sb.append(ns.statement.statementString(indent, ns));
            ns = ns.next.isSet() ? ns.next.get().orElse(null) : null;
        }
        return sb.toString();
    }

    @Override
    public Set<String> imports() {
        Set<String> imports = new HashSet<>();
        for (Statement statement : statements) {
            imports.addAll(statement.imports());
        }
        return ImmutableSet.copyOf(imports);
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        return statements.stream()
                .map(s -> s.sideEffect(evaluationContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);
    }

    @Override
    public CodeOrganization codeOrganization() {
        return new CodeOrganization.Builder()
                .setStatementsExecutedAtLeastOnce(v -> true)
                .setNoBlockMayBeExecuted(false)
                .setStatements(this).build();
    }

    @Override
    public List<Statement> getStatements() {
        return statements;
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
        return mostSpecific.get() == null ? Primitives.PRIMITIVES.voidParameterizedType: mostSpecific.get();
    }

    public void visit(Consumer<Statement> consumer) {
        statements.forEach(statement -> statement.visit(consumer));
        consumer.accept(this);
    }

    @Override
    public Statement translate(TranslationMap translationMap) {
        if (this == EMPTY_BLOCK) return this;
        return new Block(statements.stream().flatMap(st -> translationMap.translateStatement(st).stream()).collect(Collectors.toList()), label);
    }
}
