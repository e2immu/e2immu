package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.Analysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.MethodAnalysisImpl;
import org.e2immu.analyser.analysis.impl.ParameterAnalysisImpl;
import org.e2immu.analyser.analysis.impl.TypeAnalysisImpl;
import org.e2immu.analyser.inspector.MethodResolution;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.inspector.impl.ParameterInspectionImpl;
import org.e2immu.analyser.inspector.impl.TypeInspectionImpl;
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

    @Test
    @DisplayName("setter on mutable type, o~b.set(p0~a), independent hc")
    public void test1d() {
        MethodInfo method = methodCallOneArgument(MultiLevel.INDEPENDENT_HC_DV, CS_NONE);
        assertThrows(AssertionError.class, () -> evaluateMethodCallOneArgument(method));

        MethodInfo method2 = methodCallOneArgument(MultiLevel.INDEPENDENT_HC_DV, LV.selectTypeParameter(0));
        EvaluationResult er = evaluateMethodCallOneArgument(method2);

        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertEquals(2, er.changeData().size());
        ChangeData ca = er.findChangeData("a");
        assertEquals("b:4", ca.linkedVariables().toString());
        ChangeData cb = er.findChangeData("b");
        assertEquals("a:4", cb.linkedVariables().toString());
        assertTrue(cb.linkedVariables().causesOfDelay().isDone());
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


    @Test
    @DisplayName(".forEach(e -> a.add(e))")
    public void test2() {
        MethodInfo method = methodCallConsumer(MultiLevel.DEPENDENT_DV, CS_NONE);

        EvaluationResult er = evaluateMethodCallConsumer(method);
        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertEquals(2, er.changeData().size());
        ChangeData ca = er.findChangeData("a");
        assertEquals("b:2", ca.linkedVariables().toString());
        ChangeData cb = er.findChangeData("b");
        assertEquals("a:2", cb.linkedVariables().toString());
        assertTrue(cb.linkedVariables().causesOfDelay().isDone());
    }


    private EvaluationResult evaluateMethodCallConsumer(MethodInfo method) {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero);
        VariableExpression vb = makeLVAsExpression("b", zero);
        TypeInfo consumerTypeInfo = typeMapBuilder.syntheticFunction(1, true);
        MethodInfo abstractSam = consumerTypeInfo.findUniqueMethod("accept", 1);
        ParameterizedType abstractFunctionalType = consumerTypeInfo.asParameterizedType(inspectionProvider);
        ParameterizedType implementationInterfaceType = new ParameterizedType(consumerTypeInfo, List.of(mutablePt));

        TypeInfo implementation = new TypeInfo("com.foo", "I");
        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                mutablePt, "p0", 0);
        MethodInfo sam = new MethodInspectionImpl.Builder(implementation, "accept",
                MethodInfo.MethodType.METHOD)
                .addParameter(param0Inspection)
                .setAccess(Inspection.Access.PUBLIC)
                .setReturnType(primitives.voidParameterizedType())
                .build(inspectionProvider).getMethodInfo();
        MethodResolution samMr = new MethodResolution(Set.of(abstractSam), Set.of(), MethodResolution.CallStatus.NON_PRIVATE,
                false, Set.of(), false);
        sam.methodResolution.set(samMr);
        ParameterInfo p0 = sam.methodInspection.get().getParameters().get(0);
        ParameterAnalysis p0Analysis = (ParameterAnalysis) new ParameterAnalysisImpl.Builder(primitives, analysisProvider, p0)
                .setHiddenContentSelector(LV.selectTypeParameter(0))
                .build();

        StatementAnalysis firstStatement = new StatementAnalysis() {
            @Override
            public VariableInfo getLatestVariableInfo(String variableName) {
                return new VariableInfo() {
                    @Override
                    public boolean linkedVariablesIsSet() {
                        return true;
                    }

                    @Override
                    public LinkedVariables getLinkedVariables() {
                        return LinkedVariables.of(va.variable(), LV.LINK_DEPENDENT);
                    }
                };
            }

            @Override
            public int compareTo(StatementAnalysis o) {
                throw new UnsupportedOperationException();
            }

            @Override
            public StatementAnalysis lastStatement(boolean excludeThrows) {
                return this;
            }
        };

        MethodAnalysisImpl.Builder samAnaBuilder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, sam, primitives.stringTypeInfo().typeAnalysis.get(),
                List.of(p0Analysis))
                .setFirstStatement(firstStatement)
                .setHiddenContentSelector(CS_NONE);
        sam.methodAnalysis.set(samAnaBuilder.build());
        implementation.typeInspection.set(new TypeInspectionImpl.Builder(implementation, Inspector.BY_HAND)
                .addInterfaceImplemented(implementationInterfaceType)
                .setParentClass(primitives.objectParameterizedType())
                .setFunctionalInterface(sam.methodInspection.get())
                .addMethod(sam)
                .build(inspectionProvider));
        ParameterizedType implementationPt = implementation.asParameterizedType(inspectionProvider);

        Lambda concreteConsumer = new Lambda(newId(), inspectionProvider, abstractFunctionalType, implementationPt,
                primitives.voidParameterizedType(), List.of(Lambda.OutputVariant.EMPTY));
        ExpressionMock object = simpleMock(mutablePt, LinkedVariables.of(vb.variable(), LV.LINK_DEPENDENT));

        MethodCall mc = new MethodCall(newId(), object, method, List.of(concreteConsumer));
        Expression thisMock = simpleMock(primitives.stringParameterizedType(), LinkedVariables.EMPTY);

        EvaluationContext ec = evaluationContext(Map.of("this", thisMock, "a", va));
        return mc.evaluate(context(ec), ForwardEvaluationInfo.DEFAULT);
    }

    private MethodInfo methodCallConsumer(DV independentP0, LV.HiddenContentSelector p0Hcs) {
        TypeInfo consumerTypeInfo = typeMapBuilder.syntheticFunction(1, true);
        ParameterizedType consumer = new ParameterizedType(consumerTypeInfo, List.of(mutablePt));
        assertEquals("Type _internal_.SyntheticConsumer1<com.foo.Mutable>", consumer.toString());

        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                consumer, "p0", 0);

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
