package org.e2immu.analyser.testexample;

import org.e2immu.analyser.testexample.a.IFluent_2;
import org.e2immu.annotation.*;

import javax.annotation.processing.Generated;
import java.util.Objects;

/*
Identical to Fluent_1 apart from reference to IFluent_2
 */
@E2Container
@Generated("org.e2immu.analyser.testexample.a.IFluent_2")
public class Fluent_2 implements IFluent_2 {

    public final int value;

    private Fluent_2(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return value;
    }

    public final Fluent_2 withValue(int value) {
        if (this.value == value) return this;
        return new Fluent_2(value);
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Fluent_2 && equalTo((Fluent_2) another);
    }

    private boolean equalTo(Fluent_2 another) {
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

    public static Fluent_2 copyOf(IFluent_2 instance) {
        if (instance instanceof Fluent_2) {
            return (Fluent_2) instance;
        }
        return new IFluent_2.Builder().from(instance).build();
    }

    public static Fluent_2 copyOf2(IFluent_2 instance) {
        return new IFluent_2.Builder().from(instance).build();
    }

    @Identity
    public static Fluent_2 identity(IFluent_2 instance) {
       return (Fluent_2) instance;
    }


    @Container
    public static class Builder {
        private int value;

        public Builder() {
            if (!(this instanceof IFluent_2.Builder)) {
                throw new UnsupportedOperationException("Use: new Primitive.Builder()");
            }
        }

        @Fluent
        @Modified
        public final IFluent_2.Builder from(IFluent_2 instance) {
            Objects.requireNonNull(instance, "instance");
            value(instance.value());
            return (IFluent_2.Builder) this;
        }

        @Fluent
        @Modified
        public final IFluent_2.Builder value(int value) {
            this.value = value;
            return (IFluent_2.Builder) this;
        }

        @NotModified
        public Fluent_2 build() {
            return new Fluent_2(value);
        }
    }
}
