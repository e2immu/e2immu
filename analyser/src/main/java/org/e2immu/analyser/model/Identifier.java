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
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.IntConstant;
import org.e2immu.annotation.FinalFields;
import org.e2immu.annotation.ImmutableContainer;
import org.e2immu.annotation.Independent;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.rare.IgnoreModifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/*
An Element needs an identifier, to distinguish it from other structurally similar elements.
At the same time, we may need location information (line, pos) to report errors.

We'll allow for multiple types of identifiers, because we also generate code (a lot of it for internal purposes,
never seen by the user); in this situation, it's easier to work with unique integers.

Identifier orders:

Positional (JavaParser) 0
Jar (ByteCodeInspector) 1
Incremental 2
ListOf 3
LoopCondition 4
State 5
Constant 6
StatementTime 8

 */
public interface Identifier extends Comparable<Identifier> {
    Logger LOGGER = LoggerFactory.getLogger(Identifier.class);
    int identifierOrder();


    Identifier CONSTANT = new IncrementalIdentifier("constant");
    Identifier INTERNAL_TYPE = new IncrementalIdentifier("internal type");
    Identifier NOT_ACCESSIBLE = new IncrementalIdentifier("type not accessible");

    default boolean isInternalType() {
        return INTERNAL_TYPE == this;
    }

    @Independent
    static Identifier constant(@NotNull Object object) {
        return new ConstantIdentifier(object.getClass().getSimpleName() + ":" + object.hashCode());
    }

    @Independent
    static Identifier from(Node node) {
        if (node == null) return Identifier.generate("null node");
        Optional<Position> begin = node.getBegin();
        if (begin.isEmpty()) return new IncrementalIdentifier("null begin");
        Optional<Position> end = node.getEnd();
        return from(begin.get(), end.orElseThrow());
    }

    // FIXME make a distinction between .java and .class URIs?
    //  or only store the JAR part? see ByteCodeInspector.inspectFromPath
    @Independent
    static Identifier from(URI uri) {
        return new JarIdentifier(uri);
    }

    @Independent
    static PositionalIdentifier positionFrom(@NotNull Node node) {
        assert node != null;
        Optional<Position> begin = node.getBegin();
        Optional<Position> end = node.getEnd();
        return from(begin.orElseThrow(), end.orElseThrow());
    }

    @Independent
    static PositionalIdentifier from(Position begin, Position end) {
        if (begin == null) return null;
        return new PositionalIdentifier((short) begin.line, (short) begin.column,
                (short) end.line, (short) end.column);
    }

    static Identifier generate(String origin) {
        return new IncrementalIdentifier(origin);
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

    static Identifier forStatementTime(int statementTime) {
        return new StatementTimeIdentifier(statementTime);
    }

    String compact();


    /*
    Except a few basic expressions (boolean constant, trivial ints, empty), expressions are supposed to have
    a positional identifier, identifying their origin in the source code. This method verifies this.
     */
    static boolean isListOfPositionalIdentifiers(Expression expression) {
        AtomicBoolean success = new AtomicBoolean(true);
        expression.visit(e -> {
            // omnipresent
            if (e instanceof BooleanConstant) return false;
            // -1,0,1: why? because we need these small constants for trivial computations, e.g. in GreaterThan
            if (e instanceof IntConstant ic && ic.complexity == 1) return false;
            if (e instanceof EmptyExpression) return false;
            if (e instanceof Expression exp) {
                if (!acceptIdentifier(exp.getIdentifier())) {
                    success.set(false);
                    LOGGER.error("Expression {} of {} has identifier of {}", exp, exp.getClass(),
                            exp.getIdentifier().getClass());
                    return false;
                }
            }
            return true;
        });
        return success.get();
    }

    static boolean acceptIdentifier(Identifier identifier) {
        return identifier instanceof Identifier.PositionalIdentifier ||
                identifier instanceof VariableIdentifier || // TODO
                identifier instanceof StatementTimeIdentifier || // TODO we should clarify their use
                identifier instanceof Identifier.ListOfIdentifiers list &&
                        list.identifiers().stream().anyMatch(Identifier::acceptIdentifier);
    }

    @ImmutableContainer
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

    @ImmutableContainer
    class IncrementalIdentifier implements Identifier {
        @IgnoreModifications
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
            return 2;
        }

        @Override
        public String compact() {
            return "ID:" + identifier;
        }
    }

    // FIXME is this still in use?
    @FinalFields
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
            return 3;
        }

        @Override
        public String compact() {
            return "ID:" + identifiers.stream().map(Identifier::compact).collect(Collectors.joining(","));
        }
    }

    @ImmutableContainer
    record LoopConditionIdentifier(String index) implements Identifier {
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
            return "L:" + index;
        }
    }

    @ImmutableContainer
    record StateIdentifier(String index) implements Identifier {
        @Override
        public int compareTo(Identifier o) {
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 5;
        }

        @Override
        public String compact() {
            return "S:" + index;
        }
    }

    @ImmutableContainer
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

    @ImmutableContainer
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

    record JarIdentifier(URI uri) implements Identifier {

        @Override
        public int compareTo(Identifier o) {
            if (o instanceof JarIdentifier ji) {
                return uri.compareTo(ji.uri);
            }
            return identifierOrder() - o.identifierOrder();
        }

        @Override
        public int identifierOrder() {
            return 0;
        }

        @Override
        public String compact() {
            return uri.toString();
        }
    }

    // implicitly: @NotModified, so you cannot turn this into a modifying one.
    default boolean unstableIdentifier() {
        return false;
    }
}
