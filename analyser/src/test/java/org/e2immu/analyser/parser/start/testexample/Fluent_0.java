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

package org.e2immu.analyser.parser.start.testexample;

import org.e2immu.analyser.parser.start.testexample.a.IFluent_0;
import org.e2immu.annotation.*;

import java.util.Objects;

@ERContainer
public class Fluent_0 implements IFluent_0 {

    public final int value;

    private Fluent_0(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return value;
    }

    public final Fluent_0 withValue(int value) {
        if (this.value == value) return this;
        return new Fluent_0(value);
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Fluent_0 && equalTo((Fluent_0) another);
    }

    private boolean equalTo(Fluent_0 another2) {
        return value == another2.value;
    }

    /**
     * Computes a hash code from attributes: {@code value}.
     *
     * @return hashCode value
     */
    @Override
    public int hashCode() {
        int h = 5381;
        h += (h << 5) + value;
        return h;
    }

    /**
     * Prints the immutable value {@code Primitive} with attribute values.
     *
     * @return A string representation of the value
     */
    @Override
    public String toString() {
        return "Primitive{"
                + "value=" + value
                + "}";
    }

    public static Fluent_0 copyOf(IFluent_0 instanceCopy) {
        if (instanceCopy instanceof Fluent_0) {
            return (Fluent_0) instanceCopy;
        }
        return new IFluent_0.Builder().from(instanceCopy).build();
    }

    public static Fluent_0 copyOf2(IFluent_0 instance2) {
        return new IFluent_0.Builder().from(instance2).build();
    }

    @Identity
    public static Fluent_0 identity(IFluent_0 instanceIdentity) {
       return (Fluent_0) instanceIdentity;
    }


    @Container
    public static class Builder {
        private int value;

        public Builder() {
            if (!(this instanceof IFluent_0.Builder)) {
                throw new UnsupportedOperationException("Use: new Primitive.Builder()");
            }
        }

        @Fluent
        @Modified
        public final IFluent_0.Builder from(IFluent_0 instanceFrom) {
            if(instanceFrom == null) throw new NullPointerException();
            value(instanceFrom.value());
            return (IFluent_0.Builder) this;
        }

        @Fluent
        @Modified
        public final IFluent_0.Builder value(int value) {
            this.value = value;
            return (IFluent_0.Builder) this;
        }

        @NotModified
        @NotNull
        public Fluent_0 build() {
            return new Fluent_0(value);
        }
    }
}
