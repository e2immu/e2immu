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

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/*
An Element needs an identifier, to distinguish it from other structurally similar elements.
At the same time, we may need location information (line, pos) to report errors.

We'll allow for multiple types of identifiers, because we also generate code (a lot of it for internal purposes,
never seen by the user); in this situation, it's easier to work with unique integers.
 */
public interface Identifier {

    Identifier CONSTANT = new IncrementalIdentifier();

    static Identifier from(Node node) {
        if(node == null) return Identifier.generate();
        Optional<Position> position = node.getBegin();
        if (position.isEmpty()) return new IncrementalIdentifier();
        Position p = position.get();
        return new PositionalIdentifier(p.line, p.column);
    }

    static Identifier generate() {
        return new IncrementalIdentifier();
    }

    record PositionalIdentifier(int line, int pos) implements Identifier {
    }

    class IncrementalIdentifier implements Identifier {
        private static final AtomicInteger generator = new AtomicInteger();
        public final int identifier;

        public IncrementalIdentifier() {
            identifier = generator.incrementAndGet();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IncrementalIdentifier that = (IncrementalIdentifier) o;
            return identifier == that.identifier;
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier);
        }
    }
}
