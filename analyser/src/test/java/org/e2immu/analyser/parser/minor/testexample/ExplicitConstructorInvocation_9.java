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


// Important in this test is that the method LoopStatement is analysed before StatementWithExpression

public class ExplicitConstructorInvocation_9 {
    interface Identifier {
    }

    interface Expression {
    }

    record Structure(Expression expression) {
    }

    static abstract class StatementWithExpression {
        protected final Identifier identifier;
        protected final Expression expression;

        StatementWithExpression(Identifier identifier, Structure structure, Expression expression) {
            this.identifier = identifier;
            this.expression = expression;
            // intentionally leaving out "structure"
        }
    }

    public abstract static class LoopStatement extends StatementWithExpression {
        public final String label;

        protected LoopStatement(Identifier identifier, Structure structure, String label) {
            super(identifier, structure, structure.expression());
            this.label = label;
        }
    }

}
