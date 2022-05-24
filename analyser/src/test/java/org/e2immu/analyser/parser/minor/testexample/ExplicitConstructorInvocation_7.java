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

package org.e2immu.analyser.parser.minor.testexample;

import java.util.List;
import java.util.Random;

// attempt at re-creating a delay problem with the constructors of And

public class ExplicitConstructorInvocation_7 {

    interface Identifier {
        int getComplexity();
    }

    static class Expression {
        private final Random random = new Random();

        int getComplexity() {
            return random.nextInt();
        }
    }

    interface Primitives {
    }

    static <T> T requireNonNull(T o) {
        assert o != null;
        return o;
    }

    private static final int COMPLEXITY = 3;

    private final Primitives primitives;
    private final List<Expression> expressions;
    private final int complexity;

    public ExplicitConstructorInvocation_7(Primitives primitives1, List<Expression> expressions) {
        this(null, primitives1, expressions);
    }

    private ExplicitConstructorInvocation_7(Identifier identifier, Primitives primitives2, List<Expression> expressions) {
        this.complexity = COMPLEXITY + expressions.stream().mapToInt(Expression::getComplexity).sum()
                + (identifier == null ? 0 : identifier.getComplexity());
        this.primitives = requireNonNull(primitives2);
        this.expressions = requireNonNull(expressions);
    }

    private ExplicitConstructorInvocation_7(Identifier identifier, Primitives primitives3) {
        this(identifier, primitives3, List.of());
    }

    public List<Expression> getExpressions() {
        return expressions;
    }

    public Primitives getPrimitives() {
        return primitives;
    }

    public int getComplexity() {
        return complexity;
    }
}
