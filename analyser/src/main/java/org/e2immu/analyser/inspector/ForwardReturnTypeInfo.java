package org.e2immu.analyser.inspector;

import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.parser.Primitives;

/*
Information about the return type of an expression, passed on in a forwarding way.
 */
public record ForwardReturnTypeInfo(ParameterizedType type, MethodTypeParameterMap sam) {

    // we'd rather have java.lang.Boolean, because as soon as type parameters are involved, primitives
    // are boxed
    public static ForwardReturnTypeInfo expectBoolean(TypeContext typeContext) {
        return new ForwardReturnTypeInfo(typeContext.getPrimitives()
                .boxedBooleanTypeInfo.asSimpleParameterizedType(), null);
    }

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
