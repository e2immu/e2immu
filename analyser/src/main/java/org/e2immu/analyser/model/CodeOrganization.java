package org.e2immu.analyser.model;

import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NullNotAllowed;

import java.util.List;
import java.util.Objects;

/**
 * <ul>
 *     <li>expression as statement: E [-]</li>
 *     <li>block: [-B]</li>
 *     <li>forEach: E [-B] or [E-B]</li>
 *     <li>for: [EEE-B]</li>
 *     <li>try: [EEE-B E-B E-B -B]</li>
 *     <li>if: E [-B -B]</li>
 *     <li>switch: E [ EEE-B EEE-B ] </li>
 * </ul>
 */
public class CodeOrganization {

    public Expression expression;
    @NotNull
    public List<ExpressionsWithStatements> expressionsWithStatements;

    public CodeOrganization(Expression expression, @NullNotAllowed List<ExpressionsWithStatements> expressionsWithStatements) {
        this.expression = expression;
        this.expressionsWithStatements = Objects.requireNonNull(expressionsWithStatements);
    }

    public static class ExpressionsWithStatements {
        @NotNull
        public List<Expression> expressions;
        public HasStatements statements;

        public ExpressionsWithStatements(@NullNotAllowed List<Expression> expressions, HasStatements statements) {
            this.expressions = Objects.requireNonNull(expressions);
            this.statements = statements;
        }
    }

}
