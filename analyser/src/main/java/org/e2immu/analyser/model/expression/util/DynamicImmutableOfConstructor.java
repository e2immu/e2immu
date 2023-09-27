package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.util.ComputeIndependent;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DynamicImmutableOfConstructor {

    private final EvaluationResult context;
    private final List<Expression> parameterExpressions;
    private final ParameterizedType concreteReturnType;
    private final MethodInfo methodInfo;

    public DynamicImmutableOfConstructor(EvaluationResult context,
                                         MethodInfo methodInfo,
                                         List<Expression> parameterExpressions,
                                         ParameterizedType concreteReturnType) {
        this.context = Objects.requireNonNull(context);
        this.methodInfo = Objects.requireNonNull(methodInfo);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.concreteReturnType = Objects.requireNonNull(concreteReturnType);
    }

    /*
    The concrete immutability value of constructors and factory methods is partially determined by the immutability
    values of the parameter expressions (again, concrete, not formal).
    See e.g. E2Immutable_17, SMapList, Lambda_AAPI_10, ...
     */
    public DV compute(DV formal) {
        if (parameterExpressions.isEmpty()
                || MultiLevel.MUTABLE_DV.equals(formal)
                || MultiLevel.isAtLeastEventuallyRecursivelyImmutable(formal)) {
            // the two ends of the spectrum cannot change, and neither do we have any influence without parameters
            return formal;
        }
        DV params = valueOverParameters();
        DV nonLinkedFields = valueOverNonLinkedFields();
        return nonLinkedFields.min(params).max(formal);
    }

    private DV valueOverNonLinkedFields() {
        CausesOfDelay linkDelays = delaysOfFieldsLinkedToConstructor();
        if (linkDelays.isDelayed()) return linkDelays;
        TypeInspection typeInspection = context.getAnalyserContext().getTypeInspection(methodInfo.typeInfo);
        Map<FieldInfo, DV> fieldsLinkedToConstructor = fieldsLinkedToConstructor();
        return typeInspection.fields().stream()
                .filter(f -> !fieldsLinkedToConstructor.containsKey(f))
                .map(this::immutableOfField)
                .reduce(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, DV::min);
    }

    private CausesOfDelay delaysOfFieldsLinkedToConstructor() {
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);
        return methodAnalysis.getParameterAnalyses()
                .stream()
                .map(ParameterAnalysis::assignedToFieldDelays)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
    }

    private Map<FieldInfo, DV> fieldsLinkedToConstructor() {
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);
        Map<FieldInfo, DV> result = new HashMap<>();
        for (ParameterAnalysis pa : methodAnalysis.getParameterAnalyses()) {
            for (Map.Entry<FieldInfo, DV> entry : pa.getAssignedToField().entrySet()) {
                result.merge(entry.getKey(), entry.getValue(), DV::min);
            }
        }
        return result;
    }


    private DV immutableOfField(FieldInfo fieldInfo) {
        FieldAnalysis fieldAnalysis = context.getAnalyserContext().getFieldAnalysis(fieldInfo);
        return fieldAnalysis.getProperty(Property.EXTERNAL_IMMUTABLE);
    }

    /*
     TODO the current implementation is simplistic. We'll need to take into account the possible effects of
       linking at a level higher than 2 (MUTABLE)
    */
    private DV valueOverParameters() {
        // immutable with hidden content, the result is determined by the immutability values of the parameters
        DV minParams = MultiLevel.EFFECTIVELY_IMMUTABLE_DV;
        int i = 0;
        MethodAnalysis methodAnalysis = context.getAnalyserContext().getMethodAnalysis(methodInfo);
        MethodInspection methodInspection = context.getAnalyserContext().getMethodInspection(methodInfo);
        CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;
        for (Expression pe : parameterExpressions) {
            if (pe.isNullConstant()) continue;
            DV immutable = context.getProperty(pe, Property.IMMUTABLE);
            if (!MultiLevel.isAtLeastEventuallyRecursivelyImmutable(immutable)) {
                ParameterAnalysis parameterAnalysis = methodAnalysis.getParameterAnalyses().get(i);
                DV independent = parameterAnalysis.getProperty(Property.INDEPENDENT);
                if (independent.isDelayed()) {
                    causesOfDelay = causesOfDelay.merge(independent.causesOfDelay());
                } else if (MultiLevel.INDEPENDENT_DV.gt(independent)) {
                    DV dv;
                    if (MultiLevel.INDEPENDENT_HC_DV.equals(independent)) {
                        ComputeIndependent ci = new ComputeIndependent(context.getAnalyserContext(), context.getCurrentType());
                        ParameterizedType formalParameterType = methodInspection.formalParameterType(i);
                        DV linkLevel = ci.directedLinkLevelOfTwoHCRelatedTypes(formalParameterType,
                                methodInspection.getReturnType());
                        boolean shareHiddenContent = LinkedVariables.LINK_COMMON_HC.equals(linkLevel);
                        if (shareHiddenContent) {
                            dv = ci.immutableOfIntersectionOfHiddenContent(pe.returnType(), concreteReturnType);
                        } else {
                            dv = immutable;
                        }
                    } else {
                        // dependent, directly take immutable value
                        dv = immutable;
                    }
                    minParams = minParams.min(dv);
                } // else: ignore
            }
            i++;
        }
        if (minParams.isDelayed() || causesOfDelay.isDelayed()) {
            return minParams.causesOfDelay().merge(causesOfDelay);
        }
        return minParams;
    }

}
