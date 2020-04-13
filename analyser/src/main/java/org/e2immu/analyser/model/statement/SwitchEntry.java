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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.analyser.util.SetUtil;
import org.e2immu.analyser.util.StringUtil;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public abstract class SwitchEntry implements Statement {

    public final List<Expression> labels;

    private SwitchEntry(List<Expression> labels) {
        this.labels = labels;
    }

    protected void appendLabels(StringBuilder sb, int indent, boolean java12Style, boolean newLine) {
        if (java12Style) {
            sb.append(labels.stream().map(l -> l.expressionString(0)).collect(Collectors.joining(", ")));
            sb.append(" ->");
            if (newLine) sb.append("\n");
        } else {
            boolean multiLine = labels.size() > 1 || newLine;
            for (Expression label : labels) {
                sb.append(label.expressionString(indent));
                sb.append(":");
                if (multiLine) sb.append("\n");
            }
        }
    }

    public abstract CodeOrganization.ExpressionsWithStatements toExpressionsWithStatements();

    public static class StatementsEntry extends SwitchEntry implements HasStatements {
        public final List<Statement> statements;
        public final boolean java12Style;

        public StatementsEntry(boolean java12Style, List<Expression> labels, List<Statement> statements) {
            super(labels);
            this.java12Style = java12Style;
            this.statements = statements;
        }

        @Override
        public CodeOrganization.ExpressionsWithStatements toExpressionsWithStatements() {
            return new CodeOrganization.ExpressionsWithStatements(labels, this);
        }

        @Override
        public String statementString(int indent) {
            StringBuilder sb = new StringBuilder();
            appendLabels(sb, indent, java12Style, statements.size() > 1);
            if (statements.size() == 1) {
                sb.append(" ");
                sb.append(statements.get(0).statementString(0));
                sb.append("\n");
            } else {
                for (Statement statement : statements) {
                    StringUtil.indent(sb, indent + 4);
                    sb.append(statement.statementString(indent + 4));
                    sb.append("\n");
                }
            }
            return sb.toString();
        }

        @Override
        public List<Statement> getStatements() {
            return statements;
        }

        @Override
        public Set<String> imports() {
            return SetUtil.immutableUnion(
                    labels.stream().flatMap(s -> s.imports().stream()).collect(Collectors.toSet()),
                    statements.stream().flatMap(s -> s.imports().stream()).collect(Collectors.toSet()));
        }

        @Override
        public SideEffect sideEffect(SideEffectContext sideEffectContext) {
            SideEffect sideEffect = labels.stream().map(s -> s.sideEffect(sideEffectContext))
                    .reduce(SideEffect.LOCAL, SideEffect::combine);
            return statements.stream().map(s -> s.sideEffect(sideEffectContext))
                    .reduce(sideEffect, SideEffect::combine);
        }
    }

    public static class BlockEntry extends SwitchEntry {
        public final Block block;

        public BlockEntry(List<Expression> labels, Block block) {
            super(labels);
            this.block = block;
        }

        @Override
        public String statementString(int indent) {
            StringBuilder sb = new StringBuilder();
            appendLabels(sb, indent, true, false);
            sb.append(block.statementString(indent));
            return sb.toString();
        }

        @Override
        public Set<String> imports() {
            return SetUtil.immutableUnion(labels.stream().flatMap(s -> s.imports().stream()).collect(Collectors.toSet()),
                    block.imports());
        }

        @Override
        public SideEffect sideEffect(SideEffectContext sideEffectContext) {
            SideEffect sideEffect = labels.stream().map(s -> s.sideEffect(sideEffectContext))
                    .reduce(SideEffect.LOCAL, SideEffect::combine);
            return sideEffect.combine(block.sideEffect(sideEffectContext));
        }

        @Override
        public CodeOrganization.ExpressionsWithStatements toExpressionsWithStatements() {
            return new CodeOrganization.ExpressionsWithStatements(labels, block);
        }
    }
}
