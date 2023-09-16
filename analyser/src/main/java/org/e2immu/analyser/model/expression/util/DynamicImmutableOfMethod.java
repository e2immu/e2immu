package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DynamicImmutableOfMethod {
    private final List<Expression> parameterExpressions;
    private final MethodInfo methodInfo;

    private final ParameterizedType concreteReturnType;
    private final EvaluationResult context;


    public DynamicImmutableOfMethod(EvaluationResult context, MethodInfo methodInfo,
                                    List<Expression> parameterExpressions,
                                    ParameterizedType concreteReturnType) {
        this.parameterExpressions = parameterExpressions;
        this.methodInfo = methodInfo;
        this.concreteReturnType = concreteReturnType;
        this.context = context;
    }

    public DV dynamicImmutable(DV formalMethodImmutable, MethodAnalysis methodAnalysis) {
        DV identity = methodAnalysis.getProperty(Property.IDENTITY);
        if (identity.isDelayed()) return identity;
        if (identity.valueIsTrue()) {
            return context.evaluationContext().getProperty(parameterExpressions.get(0), Property.IMMUTABLE,
                    true, true);
        }
        AnalyserContext analyserContext = context.getAnalyserContext();
        MethodInspection methodInspection = analyserContext.getMethodInspection(methodInfo);

        /*
        There is one situation where we need to deviate from the value of the concrete return type: when we know
        through analysis of the statement, that the dynamic immutable value is different from the formal one.

        E2Immutable_11: method firstEntry() should be immutable, rather than mutable. Must go through the
        immutableDeterminedByTypeParameters() code!

        Enum_6: method returnTwo must return mutable, even if the formal value is higher
        MethodReferences_3: method stream() must return mutable: the type parameter is mutable, formal=dynamic is higher
         */
        if (MultiLevel.MUTABLE_DV.equals(formalMethodImmutable) ||
                MultiLevel.isAtLeastEventuallyRecursivelyImmutable(formalMethodImmutable)) {
            // the two extremes stay the way they are
            return formalMethodImmutable;
        }
        if (formalMethodImmutable.isDelayed()) return formalMethodImmutable;

        DV formalReturnTypeImmutable = analyserContext.typeImmutable(methodInspection.getReturnType());
        if (formalReturnTypeImmutable.isDelayed()) return formalReturnTypeImmutable;

        DV concreteReturnTypeImmutable = analyserContext.typeImmutable(concreteReturnType);
        if (concreteReturnTypeImmutable.isDelayed()) return concreteReturnTypeImmutable;

        if (formalMethodImmutable.equals(formalReturnTypeImmutable)) {
            /*
             there is no difference between the immutability of a method and the immutability of its return type,
             so we don't have to do any acrobatics
             */
            return concreteReturnTypeImmutable;
        }

        assert MultiLevel.isMutable(formalReturnTypeImmutable);

        /*
        TODO we need to find out which of the type parameters are actually linked to fields.
         For now, we're assuming that all of them are!!
         */
        TypeParameterMap typeParameterMap = computeTypeParameterMap(analyserContext, concreteReturnType);
        if (typeParameterMap.causes.isDelayed()) return typeParameterMap.causes;
        return analyserContext.typeImmutable(concreteReturnType, typeParameterMap.map);
    }


    private record TypeParameterMap(CausesOfDelay causes, Map<ParameterizedType, DV> map) {
    }

    private static final TypeParameterMap EMPTY = new TypeParameterMap(CausesOfDelay.EMPTY, Map.of());

    /*
    type starts off as 'concrete return type', but there is a recursion
     */
    private TypeParameterMap computeTypeParameterMap(AnalyserContext analyserContext, ParameterizedType type) {
        if (type.typeInfo == null) return EMPTY;
        DV immutable = analyserContext.typeImmutable(type);
        if (immutable.isDelayed()) return new TypeParameterMap(immutable.causesOfDelay(), Map.of());
        if (MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) return EMPTY;

        Map<ParameterizedType, DV> map = new HashMap<>();

        CausesOfDelay causes = CausesOfDelay.EMPTY;
        if (MultiLevel.isMutable(immutable) && !type.parameters.isEmpty()) {
            map.put(type, MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV);

            /*
             all parameter types must be at least immutable_hc, for the main type to be immutable_hc; so we add them, unless
             they're obviously recursively immutable
             */
            for (ParameterizedType parameter : type.parameters) {
                TypeParameterMap typeParameterMap = computeTypeParameterMap(analyserContext, parameter);
                if (typeParameterMap.causes.isDelayed()) {
                    causes = causes.merge(typeParameterMap.causes);
                } else {
                    map.putAll(typeParameterMap.map);
                }
            }
        }
        return new TypeParameterMap(causes, map);
    }

}
