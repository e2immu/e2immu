package org.e2immu.analyser.model.abstractvalue;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.TypeContext;

public class ThisValue implements Value {
    public final This thisVariable;

    public ThisValue(This thisVariable) {
        this.thisVariable = thisVariable;
    }

    @Override
    public ParameterizedType type() {
        return thisVariable.parameterizedType();
    }

    @Override
    public int compareTo(Value o) {
        if (o instanceof This) {
            return thisVariable.typeInfo.fullyQualifiedName.compareTo(((This) o).typeInfo.fullyQualifiedName);
        }
        return -1;
    }
}
