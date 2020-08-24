package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.FieldReference;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.SideEffect;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.parser.SideEffectContext;

public class RecordField implements Variable {

    public final FieldReference fieldReference;
    public final String name;

    public RecordField(FieldReference fieldReference, String name) {
        this.name = name;
        this.fieldReference = fieldReference;
    }

    @Override
    public int variableOrder() {
        return 3;
    }

    @Override
    public ParameterizedType concreteReturnType() {
        return parameterizedType();
    }

    @Override
    public ParameterizedType parameterizedType() {
        return fieldReference.fieldInfo.type;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String detailedString() {
        return "record field "+name;
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        return fieldReference.sideEffect(sideEffectContext);
    }
}
