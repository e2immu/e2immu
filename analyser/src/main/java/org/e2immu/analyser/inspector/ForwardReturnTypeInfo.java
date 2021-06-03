package org.e2immu.analyser.inspector;

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.parser.Primitives;

/*
Information about the return type of an expression, passed on in a forwarding way.
 */
public record ForwardReturnTypeInfo(ParameterizedType type, MethodTypeParameterMap sam) {

    public static final ForwardReturnTypeInfo NO_INFO = new ForwardReturnTypeInfo(null, null);

    public static ForwardReturnTypeInfo computeSAM(ParameterizedType type, TypeContext typeContext) {
        if (type == null || Primitives.isVoid(type)) return NO_INFO;
        MethodTypeParameterMap sam = type.findSingleAbstractMethodOfInterface(typeContext);
        if (sam != null) {
            sam = sam.expand(type.initialTypeParameterMap(typeContext));
        }
        return new ForwardReturnTypeInfo(type, sam);
    }

    public boolean isVoid() {
        return type != null && Primitives.isVoid(type) || sam != null && Primitives.isVoid(sam.getConcreteReturnType());
    }
}
