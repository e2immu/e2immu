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
import org.e2immu.analyser.model.variable.This;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.LV.CS_NONE;
import static org.junit.jupiter.api.Assertions.*;

public class TestMethodCallLinkedVariablesFromParametersToObject extends CommonTest {

    @Test
    @DisplayName("setter on mutable type, o~b.set(p0~a) independent delayed")
    public void test1() {
        MethodInfo method = methodCallOneArgument(null, CS_NONE);

        EvaluationResult er = evaluateMethodCallOneArgument(method);
        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertEquals(2, er.changeData().size());
        ChangeData ca = er.findChangeData("a");
        assertEquals("b:-1", ca.linkedVariables().toString());
        ChangeData cb = er.findChangeData("b");
        assertEquals("a:-1", cb.linkedVariables().toString());
        assertEquals("independent@Parameter_p0", cb.linkedVariables().causesOfDelay().toString());
    }

    @Test
    @DisplayName("setter on mutable type, o~b.set(p0~a), dependent")
    public void test1b() {
        MethodInfo method = methodCallOneArgument(MultiLevel.DEPENDENT_DV, CS_NONE);

        EvaluationResult er = evaluateMethodCallOneArgument(method);
        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertEquals(2, er.changeData().size());
        ChangeData ca = er.findChangeData("a");
        assertEquals("b:2", ca.linkedVariables().toString());
        ChangeData cb = er.findChangeData("b");
        assertEquals("a:2", cb.linkedVariables().toString());
        assertTrue(cb.linkedVariables().causesOfDelay().isDone());
    }

    @Test
    @DisplayName("setter on mutable type, o~b.set(p0~a), independent")
    public void test1c() {
        MethodInfo method = methodCallOneArgument(MultiLevel.INDEPENDENT_DV, CS_NONE);

        EvaluationResult er = evaluateMethodCallOneArgument(method);
        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertTrue(er.changeData().isEmpty());
    }

    private EvaluationResult evaluateMethodCallOneArgument(MethodInfo method) {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero);
        VariableExpression vb = makeLVAsExpression("b", zero);

        ExpressionMock argument = simpleMock(mutablePt, LinkedVariables.of(va.variable(), LV.LINK_ASSIGNED));
        ExpressionMock object = simpleMock(mutablePt, LinkedVariables.of(vb.variable(), LV.LINK_DEPENDENT));

        MethodCall mc = new MethodCall(newId(), object, method, List.of(argument));
        Expression thisMock = simpleMock(primitives.stringParameterizedType(), LinkedVariables.EMPTY);

        EvaluationContext ec = evaluationContext(Map.of("this", thisMock, "a", va));
        return mc.evaluate(context(ec), ForwardEvaluationInfo.DEFAULT);
    }

    private MethodInfo methodCallOneArgument(DV independentP0, LV.HiddenContentSelector p0Hcs) {
        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                mutablePt, "p0", 0);

        MethodInfo method = new MethodInspectionImpl.Builder(newId(), primitives.stringTypeInfo(), "method",
                MethodInfo.MethodType.METHOD)
                .setReturnType(primitives.voidParameterizedType())
                .addParameter(param0Inspection)
                .build(inspectionProvider).getMethodInfo();
        TypeAnalysis typeAnalysis = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                primitives.stringTypeInfo(), analyserContext).build();
        ParameterInfo param0 = method.methodInspection.get().getParameters().get(0);
        ParameterAnalysisImpl.Builder p0Builder = new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param0)
                .setHiddenContentSelector(p0Hcs);
        if (independentP0 != null) {
            p0Builder.setProperty(Property.INDEPENDENT, independentP0);
        }
        ParameterAnalysis p0Analysis = (ParameterAnalysis) p0Builder.build();
        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, method, typeAnalysis,
                List.of(p0Analysis));
        builder.setProperty(Property.IDENTITY, DV.FALSE_DV);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        builder.setProperty(Property.INDEPENDENT, MultiLevel.DEPENDENT_DV);
        builder.setHiddenContentSelector(CS_NONE);
        method.setAnalysis(builder.build());

        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false);
        method.methodResolution.set(methodResolution);
        return method;
    }
}
