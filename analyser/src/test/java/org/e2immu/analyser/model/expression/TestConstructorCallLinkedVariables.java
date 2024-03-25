package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.LV.CS_NONE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestConstructorCallLinkedVariables extends CommonTest {

    @Test
    @DisplayName("empty constructor")
    public void test() {
        MethodInfo constructor = new MethodInspectionImpl.Builder(newId(), primitives.stringTypeInfo(), MethodInfo.MethodType.CONSTRUCTOR)
                .setReturnType(primitives.stringParameterizedType())
                .build(inspectionProvider).getMethodInfo();
        TypeAnalysis typeAnalysis = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                primitives.stringTypeInfo(), analyserContext).build();

        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, constructor, typeAnalysis,
                List.of());
        builder.setProperty(Property.IDENTITY, DV.FALSE_DV);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
        builder.setHiddenContentSelector(CS_NONE);
        constructor.setAnalysis(builder.build());

        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false);
        constructor.methodResolution.set(methodResolution);

        ConstructorCall cc = new ConstructorCall(newId(), null, constructor, mutablePt, Diamond.NO,
                List.of(), null, null);
        Expression thisMock = simpleMock(primitives.stringParameterizedType(), LinkedVariables.EMPTY);

        EvaluationResult er = cc.evaluate(context(evaluationContext(Map.of("this", thisMock))), ForwardEvaluationInfo.DEFAULT);
        assertEquals("", er.linkedVariablesOfExpression().toString());
    }


    @Test
    @DisplayName("direct assignment of mutable type X(M m) { this.m=m; }: new X(a);")
    public void test2() {
        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                mutablePt, "p0", 0);

        MethodInfo constructor = new MethodInspectionImpl.Builder(newId(), primitives.stringTypeInfo(),
                MethodInfo.MethodType.CONSTRUCTOR)
                .setReturnType(primitives.stringParameterizedType())
                .addParameter(param0Inspection)
                .build(inspectionProvider).getMethodInfo();
        TypeAnalysis typeAnalysis = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                primitives.stringTypeInfo(), analyserContext).build();
        ParameterInfo param0 = constructor.methodInspection.get().getParameters().get(0);
        ParameterAnalysis p0Analysis = (ParameterAnalysis) new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param0)
                .setHiddenContentSelector(CS_NONE)
                .build();
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, constructor, typeAnalysis,
                List.of(p0Analysis));
        builder.setProperty(Property.IDENTITY, DV.FALSE_DV);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        builder.setProperty(Property.INDEPENDENT, MultiLevel.DEPENDENT_DV);
        builder.setHiddenContentSelector(CS_NONE);
        constructor.setAnalysis(builder.build());

        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false);
        constructor.methodResolution.set(methodResolution);

        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero);
        ExpressionMock m = simpleMock(mutablePt, LinkedVariables.of(va.variable(), LV.LINK_ASSIGNED));
        ConstructorCall cc = new ConstructorCall(newId(), null, constructor, mutablePt, Diamond.NO,
                List.of(m), null, null);
        Expression thisMock = simpleMock(primitives.stringParameterizedType(), LinkedVariables.EMPTY);

        EvaluationContext ec = evaluationContext(Map.of("this", thisMock, "a", va));
        EvaluationResult er = cc.evaluate(context(ec), ForwardEvaluationInfo.DEFAULT);
        assertEquals("a:-1", er.linkedVariablesOfExpression().toString());
    }
}
