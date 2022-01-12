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

package org.e2immu.analyser.parser.failing.testexample;

import org.e2immu.analyser.parser.failing.testexample.a.IFluent_3;
import org.e2immu.annotation.*;

import java.util.Objects;

@E2Container
public class Fluent_3 implements IFluent_3 {

    public final int value;

    private Fluent_3(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return value;
    }

    public final Fluent_3 withValue(int value) {
        if (this.value == value) return this;
        return new Fluent_3(value);
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Fluent_3 && equalTo((Fluent_3) another);
    }

    private boolean equalTo(Fluent_3 another) {
        return value == another.value;
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

    public static Fluent_3 copyOf(IFluent_3 instance) {
        if (instance instanceof Fluent_3) {
            return (Fluent_3) instance;
        }
        return new IFluent_3.Builder().from(instance).build();
    }

    public static Fluent_3 copyOf2(IFluent_3 instance) {
        return new IFluent_3.Builder().from(instance).build();
    }

    @Identity
    public static Fluent_3 identity(IFluent_3 instance) {
       return (Fluent_3) instance;
    }


    @Container
    public static class Builder {
        private int value;

        public Builder() {
            if (!(this instanceof IFluent_3.Builder)) {
                throw new UnsupportedOperationException("Use: new Primitive.Builder()");
            }
        }

        @Fluent
        @Modified
        public final IFluent_3.Builder from(IFluent_3 instance) {
            Objects.requireNonNull(instance, "instance");
            value(instance.value());
            return (IFluent_3.Builder) this;
        }

        @Fluent
        @Modified
        public final IFluent_3.Builder value(int value) {
            this.value = value;
            return (IFluent_3.Builder) this;
        }

        @NotModified
        public Fluent_3 build() {
            return new Fluent_3(value);
        }
    }
}
