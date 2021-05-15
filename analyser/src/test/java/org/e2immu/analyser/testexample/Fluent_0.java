package org.e2immu.analyser.testexample;

import org.e2immu.analyser.testexample.a.IFluent_0;
import org.e2immu.annotation.*;

import java.util.Objects;

@E2Container
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

    private boolean equalTo(Fluent_0 another) {
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

    public static Fluent_0 copyOf(IFluent_0 instance) {
        if (instance instanceof Fluent_0) {
            return (Fluent_0) instance;
        }
        return new IFluent_0.Builder().from(instance).build();
    }

    public static Fluent_0 copyOf2(IFluent_0 instance) {
        return new IFluent_0.Builder().from(instance).build();
    }

    @Identity
    public static Fluent_0 identity(IFluent_0 instance) {
       return (Fluent_0) instance;
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
        public final IFluent_0.Builder from(IFluent_0 instance) {
            Objects.requireNonNull(instance, "instance");
            value(instance.value());
            return (IFluent_0.Builder) this;
        }

        @Fluent
        @Modified
        public final IFluent_0.Builder value(int value) {
            this.value = value;
            return (IFluent_0.Builder) this;
        }

        @NotModified
        public Fluent_0 build() {
            return new Fluent_0(value);
        }
    }
}
