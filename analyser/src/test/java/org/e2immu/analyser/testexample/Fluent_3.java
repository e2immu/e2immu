package org.e2immu.analyser.testexample;

import org.e2immu.analyser.testexample.a.IFluent_3;
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
