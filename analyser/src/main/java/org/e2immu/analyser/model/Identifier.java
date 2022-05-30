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

package org.e2immu.analyser.model;

import com.github.javaparser.Position;
import com.github.javaparser.ast.Node;
import org.e2immu.analyser.model.variable.Variable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/*
An Element needs an identifier, to distinguish it from other structurally similar elements.
At the same time, we may need location information (line, pos) to report errors.

We'll allow for multiple types of identifiers, because we also generate code (a lot of it for internal purposes,
never seen by the user); in this situation, it's easier to work with unique integers.
 */
public interface Identifier extends Comparable<Identifier> {

    int identifierOrder();

    Identifier CONSTANT = new IncrementalIdentifier("constant");

    static Identifier constant(Object object) {
        return new ConstantIdentifier(object.getClass().getSimpleName() + ":" + object.hashCode());
    }

    static Identifier from(Node node) {
        if (node == null) return Identifier.generate("null node");
        Optional<Position> begin = node.getBegin();
        if (begin.isEmpty()) return new IncrementalIdentifier("null begin");
        Optional<Position> end = node.getEnd();
        return from(begin.get(), end.orElseThrow());
    }

    static PositionalIdentifier positionFrom(Node node) {
        assert node != null;
        Optional<Position> begin = node.getBegin();
        Optional<Position> end = node.getEnd();
        return from(begin.orElseThrow(), end.orElseThrow());
    }

    static PositionalIdentifier from(Position begin, Position end) {
        if (begin == null) return null;
        return new PositionalIdentifier((short) begin.line, (short) begin.column,
                (short) end.line, (short) end.column);
    }

    static Identifier generate(String origin) {
        return new IncrementalIdentifier(origin);
    }

    static Identifier catchCondition(String index) {
        return new CatchConditionIdentifier(index);
    }

    static Identifier loopCondition(String index) {
        return new LoopConditionIdentifier(index);
    }

    static Identifier state(String index) {
        return new StateIdentifier(index);
    }

    static Identifier joined(String expression, List<Identifier> identifiers) {
        return new ListOfIdentifiers(expression, identifiers);
    }

    static Identifier forVariableOutOfScope(Variable toRemove, String index) {
        return new VariableOutOfScopeIdentifier(toRemove.fullyQualifiedName(), toRemove.simpleName(), index);
    }

    static Identifier forStatementTime(int statementTime) {
        return new StatementTimeIdentifier(statementTime);
    }

    String compact();

    record PositionalIdentifier(short line, short pos, short endLine, short endPos) implements Identifier {
        @Override
        public int compareTo(Identifier o) {
            if (o instanceof PositionalIdentifier pi) {
                int c = line - pi.line;
                if (c != 0) return c;
                return pos - pi.pos;
            }
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 0;
        }

        @Override
        public String compact() {
            return line + ":" + pos;
        }
    }

    record TestIdentifier(int i) implements Identifier {
        @Override
        public int compareTo(Identifier o) {
            if (o instanceof TestIdentifier ti) {
                return i - ti.i;
            }
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 0;
        }

        @Override
        public String compact() {
            return "I:" + i;
        }
    }

    class IncrementalIdentifier implements Identifier {
        private static final AtomicInteger generator = new AtomicInteger();
        public final String identifier;

        public IncrementalIdentifier(String origin) {
            identifier = generator.incrementAndGet() + "_" + origin;
        }

        @Override
        public boolean unstableIdentifier() {
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IncrementalIdentifier that = (IncrementalIdentifier) o;
            return identifier.equals(that.identifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier);
        }

        @Override
        public int compareTo(Identifier o) {
            if (o instanceof IncrementalIdentifier ii) return identifier.compareTo(ii.identifier);
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 1;
        }

        @Override
        public String compact() {
            return "ID:" + identifier;
        }
    }

    record ListOfIdentifiers(String expression, List<Identifier> identifiers) implements Identifier {
        @Override
        public int compareTo(Identifier o) {
            if (o instanceof ListOfIdentifiers l) {
                int c = expression.compareTo(l.expression);
                if (c != 0) return c;
                throw new UnsupportedOperationException("TODO");
            }
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 2;
        }

        @Override
        public String compact() {
            return "ID:" + identifiers.stream().map(Identifier::compact).collect(Collectors.joining(","));
        }
    }

    record LoopConditionIdentifier(String index) implements Identifier {
        @Override
        public int compareTo(Identifier o) {
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 3;
        }

        @Override
        public String compact() {
            return "L:" + index;
        }
    }

    record StateIdentifier(String index) implements Identifier {
        @Override
        public int compareTo(Identifier o) {
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 3;
        }

        @Override
        public String compact() {
            return "S:" + index;
        }
    }

    record CatchConditionIdentifier(String index) implements Identifier {
        @Override
        public int compareTo(Identifier o) {
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 4;
        }

        @Override
        public String compact() {
            return "C:" + index;
        }
    }

    record ConstantIdentifier(String constant) implements Identifier {
        @Override
        public int compareTo(Identifier o) {
            if (o instanceof ConstantIdentifier sci) return constant.compareTo(sci.constant);
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 6;
        }

        @Override
        public String compact() {
            return "S:" + constant;
        }
    }

    record VariableOutOfScopeIdentifier(String fqn, String simpleName, String index) implements Identifier {
        @Override
        public int compareTo(Identifier o) {
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 7;
        }

        @Override
        public String compact() {
            return simpleName + ":" + index;
        }
    }

    record StatementTimeIdentifier(int statementTime) implements Identifier {
        @Override
        public int compareTo(Identifier o) {
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 8;
        }

        @Override
        public String compact() {
            return "T:" + statementTime;
        }
    }

    default boolean unstableIdentifier() {
        return false;
    }
}
