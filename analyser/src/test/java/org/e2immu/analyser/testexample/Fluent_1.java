package org.e2immu.analyser.testexample;

import org.e2immu.analyser.testexample.a.IFluent_1;
import org.e2immu.annotation.*;

import javax.annotation.processing.Generated;
import java.util.Objects;

/*
Identical to Fluent_0 apart from the @Generated annotation; and referring to IFluent_1
 */
@E2Container
@Generated("org.e2immu.analyser.testexample.a.IFluent_1")
public class Fluent_1 implements IFluent_1 {

    public final int value;

    private Fluent_1(int value) {
        this.value = value;
    }

    @Override
    public int value() {
        return value;
    }

    public final Fluent_1 withValue(int value) {
        if (this.value == value) return this;
        return new Fluent_1(value);
    }

    @Override
    public boolean equals(Object another) {
        if (this == another) return true;
        return another instanceof Fluent_1 && equalTo((Fluent_1) another);
    }

    private boolean equalTo(Fluent_1 another) {
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

    public static Fluent_1 copyOf(IFluent_1 instance) {
        if (instance instanceof Fluent_1) {
            return (Fluent_1) instance;
        }
        return new IFluent_1.Builder().from(instance).build();
    }

    public static Fluent_1 copyOf2(IFluent_1 instance) {
        return new IFluent_1.Builder().from(instance).build();
    }

    @Identity
    public static Fluent_1 identity(IFluent_1 instance) {
       return (Fluent_1) instance;
    }


    @Container
    public static class Builder {
        private int value;

        public Builder() {
            if (!(this instanceof IFluent_1.Builder)) {
                throw new UnsupportedOperationException("Use: new Primitive.Builder()");
            }
        }

        @Fluent
        @Modified
        public final IFluent_1.Builder from(IFluent_1 instance) {
            Objects.requireNonNull(instance, "instance");
            value(instance.value());
            return (IFluent_1.Builder) this;
        }

        @Fluent
        @Modified
        public final IFluent_1.Builder value(int value) {
            this.value = value;
            return (IFluent_1.Builder) this;
        }

        @NotModified
        public Fluent_1 build() {
            return new Fluent_1(value);
        }
    }
}
