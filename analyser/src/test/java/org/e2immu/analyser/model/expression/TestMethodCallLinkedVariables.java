package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.ParameterInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestMethodCallLinkedVariables extends CommonTest {

    private MethodInfo methodInfo;

    @BeforeEach
    public void beforeEach() {
        super.beforeEach();
        
        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                primitives.stringParameterizedType(), "p0", 0);
        ParameterInspectionImpl.Builder param1Inspection = new ParameterInspectionImpl.Builder(newId(),
                primitives.stringParameterizedType(), "p1", 1);
        methodInfo = new MethodInspectionImpl.Builder(newId(), primitives.stringTypeInfo(), "method",
                MethodInfo.MethodType.METHOD)
                .addParameter(param0Inspection)
                .addParameter(param1Inspection)
                .setReturnType(primitives.stringParameterizedType())
                .build(inspectionProvider).getMethodInfo();
        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false);
        methodInfo.methodResolution.set(methodResolution);

        ParameterInfo param0 = methodInfo.methodInspection.get().getParameters().get(0);
        ParameterInfo param1 = methodInfo.methodInspection.get().getParameters().get(1);

        TypeAnalysis typeAnalysis = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                primitives.stringTypeInfo(), analyserContext).build();
        ParameterAnalysis p0Analysis = (ParameterAnalysis) new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param0).build();
        ParameterAnalysis p1Analysis = (ParameterAnalysis) new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param1).build();

        methodInfo.setAnalysis(new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, methodInfo, typeAnalysis,
                List.of(p0Analysis, p1Analysis)).build());
    }

    @Test
    public void test1() {
        Expression p0 = new ExpressionMock();
        Expression p1 = new ExpressionMock();
        Expression object = new ExpressionMock();
        MethodCall methodCall = new MethodCall(newId(), object, methodInfo, List.of(p0, p1));
        Expression abc = new StringConstant(primitives, "abc");
        EvaluationResult context = context(evaluationContext(Map.of("p0", abc, "p1", abc)));
        LinkedVariables lv = methodCall.linkedVariables(context);
        assertTrue(lv.isEmpty());
    }
}
