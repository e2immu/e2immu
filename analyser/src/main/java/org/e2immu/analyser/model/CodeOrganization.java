package org.e2immu.analyser.model;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.annotation.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
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
public class CodeOrganization {

    public final List<Expression> initialisers; // try, for   (example: int i=0; )
    public final LocalVariable localVariableCreation; // forEach, catch (int i,  Exception e)
    public final Expression expression; // for, forEach, while, do, return, expression statement, switch primary  (typically, the condition); OR condition for switch entry
    public final List<Expression> updaters; // for, explicit constructor invocation

    public final HasStatements statements;  // block in loops, statements or block in switch statement
    @NotNull
    public final Predicate<Value> statementsExecutedAtLeastOnce;

    public final List<CodeOrganization> subStatements; // catches, finally, switch entries

    private CodeOrganization(List<Expression> initialisers,
                             LocalVariable localVariableCreation,
                             Expression expression,
                             List<Expression> updaters,
                             HasStatements statements,
                             @NotNull Predicate<Value> statementsExecutedAtLeastOnce,
                             List<CodeOrganization> subStatements) {
        this.initialisers = Objects.requireNonNull(initialisers);
        this.localVariableCreation = localVariableCreation;
        this.expression = Objects.requireNonNull(expression);
        this.updaters = Objects.requireNonNull(updaters);
        this.statements = Objects.requireNonNull(statements);
        if (this.statements.getStatements().isEmpty() && this.statements != Block.EMPTY_BLOCK) {
            throw new UnsupportedOperationException();
        }
        this.subStatements = Objects.requireNonNull(subStatements);
        this.statementsExecutedAtLeastOnce = statementsExecutedAtLeastOnce;
    }

    public <E extends Expression> Stream<E> findExpressionRecursivelyInStatements(Class<E> clazz) {
        Stream<E> s1 = initialisers.stream().flatMap(e -> e.find(clazz).stream());
        Stream<E> s2 = Stream.concat(s1, expression.find(clazz).stream());
        Stream<E> s3 = Stream.concat(s2, updaters.stream().flatMap(e -> e.find(clazz).stream()));
        Stream<E> s4 = Stream.concat(s3, statements.getStatements().stream().flatMap(s -> Statement.findExpressionRecursivelyInStatements(s, clazz)));
        return Stream.concat(s4, subStatements.stream().flatMap(s -> s.findExpressionRecursivelyInStatements(clazz)));
    }

    public boolean haveSubBlocks() {
        return statements != Block.EMPTY_BLOCK || !subStatements.isEmpty();
    }

    public static class Builder {
        private final List<Expression> initialisers = new ArrayList<>(); // try, for   (example: int i=0; )
        private LocalVariable localVariableCreation; // forEach, catch (int i,  Exception e)
        private Expression expression; // for, forEach, while, do, return, expression statement, switch primary  (typically, the condition); OR condition for switch entry
        private final List<Expression> updaters = new ArrayList<>(); // for
        private Predicate<Value> statementsExecutedAtLeastOnce;
        private HasStatements statements;  // block in loops, statements or block in switch statement
        private final List<CodeOrganization> subStatements = new ArrayList<>(); // catches, finally, switch entries

        public Builder setExpression(Expression expression) {
            this.expression = expression;
            return this;
        }

        public Builder addInitialisers(List<Expression> initialisers) {
            this.initialisers.addAll(initialisers);
            return this;
        }

        public Builder setLocalVariableCreation(LocalVariable localVariableCreation) {
            this.localVariableCreation = localVariableCreation;
            return this;
        }

        public Builder setStatements(HasStatements statements) {
            this.statements = statements;
            return this;
        }

        public Builder addSubStatement(CodeOrganization subStatement) {
            this.subStatements.add(subStatement);
            return this;
        }

        public Builder setUpdaters(List<Expression> updaters) {
            this.updaters.addAll(updaters);
            return this;
        }

        public Builder setStatementsExecutedAtLeastOnce(Predicate<Value> predicate) {
            this.statementsExecutedAtLeastOnce = predicate;
            return this;
        }

        @NotNull
        public CodeOrganization build() {
            return new CodeOrganization(ImmutableList.copyOf(initialisers),
                    localVariableCreation,
                    expression == null ? EmptyExpression.EMPTY_EXPRESSION : expression,
                    ImmutableList.copyOf(updaters),
                    statements == null ? Block.EMPTY_BLOCK : statements,
                    statementsExecutedAtLeastOnce == null ? v -> false : statementsExecutedAtLeastOnce,
                    ImmutableList.copyOf(subStatements));
        }
    }
}
