package org.e2immu.analyser.model.value;

import org.e2immu.analyser.model.Value;

public abstract class ConstantValue implements Value {
    @Override
    public boolean isConstant() {
        return true;
    }

    @Override
    public boolean hasConstantProperties() {
        return true;
    }
}
