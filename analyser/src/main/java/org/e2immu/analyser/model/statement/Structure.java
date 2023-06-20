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

import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * <ul>
 *     <li>expression as statement: E - -: LVs, no special LVs, eval E, no blocks</li>
 *     <li>block: - B -: no LVs, no special LVs, no eval, 1 block</li>
 *     <li>forEach: E -B -, one special LV, no LVs, eval E, one block</li>
 *     <li>for: EEE - B -, no special LVs, expressions with LVs, action expressions, eval expression, one block</li>
 *     <li>if: E - B - [^E-B]: no special LVs, no LV, eval E, one block, one special block</li>
 *     <li>switch: E - [ EEE-B EEE-B ]:  no special LVs, no LV, eval E, lots of conditional blocks</li>
 *     <li>try: EEE - B - [E-B E-B True-B]</li>
 * </ul>
 */
public record Structure(List<Expression> initialisers,
                        Expression expression,
                        ForwardEvaluationInfo forwardEvaluationInfo,
                        List<Expression> updaters,
                        Block block,
                        List<Statement> statements,
                        @NotNull StatementExecution statementExecution,
                        List<Structure> subStatements,
                        boolean createVariablesInsideBlock,
                        boolean expressionIsCondition,
                        Comment comment) {

    public Structure {
        Objects.requireNonNull(initialisers);
        Objects.requireNonNull(expression);
        Objects.requireNonNull(forwardEvaluationInfo);
        Objects.requireNonNull(updaters);
        if (block != null && statements != null)
            throw new UnsupportedOperationException("Either block, or statements, but not both");
        if (block != null && block.structure.statements == null) throw new UnsupportedOperationException();
        Objects.requireNonNull(subStatements);
    }

    public List<TypeInfo> findTypeDefinedInStatement() {
        Stream<Expression> expressions = Stream.concat(Stream.concat(initialisers.stream(), updaters.stream()),
                expression == EmptyExpression.EMPTY_EXPRESSION ? Stream.empty() : Stream.of(expression));
        List<TypeInfo> types = new ArrayList<>();
        expressions.forEach(expression -> expression.visit(e -> {
            TypeInfo typeInfo = e.definesType();
            if (typeInfo != null) {
                types.add(typeInfo);
                return false;
            }
            return true;
        }));
        return types;
    }

    public List<Statement> getStatements() {
        if (block != null) return block.structure.statements;
        return statements == null ? List.of() : statements;
    }

    public boolean haveStatements() {
        if (block != null) return !block.structure.statements.isEmpty();
        return statements != null && !statements.isEmpty();
    }

    public boolean isEmptyBlock() {
        return block != null && block.structure.statements.isEmpty();
    }

    public static class Builder {
        private final List<Expression> initialisers = new ArrayList<>(); // try, for   (example: int i=0; )
        private Expression expression; // for, forEach, while, do, return, expression statement, switch primary  (typically, the condition); OR condition for switch entry
        private ForwardEvaluationInfo forwardEvaluationInfo;
        private final List<Expression> updaters = new ArrayList<>(); // for
        private StatementExecution statementExecution = StatementExecution.NEVER;
        private List<Statement> statements;  // switch statement, block itself
        private Block block;
        private final List<Structure> subStatements = new ArrayList<>(); // catches, finally, switch entries
        private boolean createVariablesInsideBlock;
        private boolean expressionIsCondition;
        private Comment comment;

        public Builder setComment(Comment comment) {
            this.comment = comment;
            return this;
        }

        public Builder setExpressionIsCondition(boolean expressionIsCondition) {
            this.expressionIsCondition = expressionIsCondition;
            return this;
        }

        public Builder setCreateVariablesInsideBlock(boolean createVariablesInsideBlock) {
            this.createVariablesInsideBlock = createVariablesInsideBlock;
            return this;
        }

        public Builder setExpression(Expression expression) {
            this.expression = expression;
            return this;
        }

        public Builder setForwardEvaluationInfo(ForwardEvaluationInfo forwardEvaluationInfo) {
            this.forwardEvaluationInfo = forwardEvaluationInfo;
            return this;
        }

        public Builder addInitialisers(List<Expression> initialisers) {
            this.initialisers.addAll(initialisers);
            return this;
        }

        public Builder setStatements(List<Statement> statements) {
            this.statements = statements;
            return this;
        }

        public Builder addSubStatement(Structure subStatement) {
            this.subStatements.add(subStatement);
            return this;
        }

        public Builder setUpdaters(List<Expression> updaters) {
            this.updaters.addAll(updaters);
            return this;
        }

        public Builder setStatementExecution(StatementExecution statementExecution) {
            this.statementExecution = statementExecution;
            return this;
        }

        public Builder setBlock(Block block) {
            this.block = block;
            return this;
        }

        @NotNull
        public Structure build() {
            return new Structure(List.copyOf(initialisers),
                    expression == null ? EmptyExpression.EMPTY_EXPRESSION : expression,
                    forwardEvaluationInfo == null ? ForwardEvaluationInfo.DEFAULT : forwardEvaluationInfo,
                    List.copyOf(updaters),
                    block,
                    statements == null ? null : List.copyOf(statements),
                    Objects.requireNonNull(statementExecution),
                    List.copyOf(subStatements),
                    createVariablesInsideBlock,
                    expressionIsCondition,
                    comment);
        }
    }
}
