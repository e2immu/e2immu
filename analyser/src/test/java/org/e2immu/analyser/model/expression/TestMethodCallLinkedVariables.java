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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.LV.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestMethodCallLinkedVariables extends CommonTest {

    private MethodInfo methodInfo;
    private final HiddenContentSelector SELECT_0 = LV.selectTypeParameter(0);

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
    }

    private void minimalMethodAnalysis(Boolean identity, DV independent, HiddenContentSelector hiddenContentSelector) {
        ParameterInfo param0 = methodInfo.methodInspection.get().getParameters().get(0);
        ParameterInfo param1 = methodInfo.methodInspection.get().getParameters().get(1);
        TypeAnalysis typeAnalysis = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                primitives.stringTypeInfo(), analyserContext).build();
        ParameterAnalysis p0Analysis = (ParameterAnalysis) new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param0).build();
        ParameterAnalysis p1Analysis = (ParameterAnalysis) new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param1).build();

        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, methodInfo, typeAnalysis,
                List.of(p0Analysis, p1Analysis));
        if (identity != null) {
            builder.setProperty(Property.IDENTITY, DV.fromBoolDv(identity));
        }
        if (independent != null) {
            builder.setProperty(Property.INDEPENDENT, independent);
        }
        if (hiddenContentSelector != null) {
            builder.setHiddenContentSelector(hiddenContentSelector);
        } else {
            builder.setHiddenContentSelector(LV.CS_NONE);
        }
        methodInfo.setAnalysis(builder.build());
    }

    @Test
    @DisplayName("base")
    public void test1() {
        minimalMethodAnalysis(null, null, null);

        Expression p0 = new ExpressionMock();
        Expression p1 = new ExpressionMock();
        Expression object = new ExpressionMock();
        MethodCall methodCall = new MethodCall(newId(), object, methodInfo, List.of(p0, p1));
        Expression abc = new StringConstant(primitives, "abc");
        EvaluationResult context = context(evaluationContext(Map.of("p0", abc, "p1", abc)));
        LinkedVariables lv = methodCall.linkedVariables(context);
        assertTrue(lv.isEmpty());
    }

    @Test
    @DisplayName("two dependent parameters, identity delayed")
    public void test2() {
        minimalMethodAnalysis(null, null, null);

        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero);
        VariableExpression vb = makeLVAsExpression("b", zero);

        Expression p0 = mockWithLinkedVariables(va, LINK_COMMON_HC_ALL);
        Expression p1 = mockWithLinkedVariables(vb, LINK_DEPENDENT);
        Expression object = new ExpressionMock();
        MethodCall methodCall = new MethodCall(newId(), object, methodInfo, List.of(p0, p1));
        Expression abc = new StringConstant(primitives, "abc");
        EvaluationResult context = context(evaluationContext(Map.of("p0", abc, "p1", abc)));
        LinkedVariables lv = methodCall.linkedVariables(context);

        assertEquals("identity@Method_method", lv.causesOfDelay().toString());
        assertEquals("a:-1", lv.toString());
    }

    @Test
    @DisplayName("two dependent parameters, identity")
    public void test3() {
        minimalMethodAnalysis(true, null, null);

        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero);
        VariableExpression vb = makeLVAsExpression("b", zero);

        for (LV dv : List.of(LINK_STATICALLY_ASSIGNED, LINK_ASSIGNED, LINK_DEPENDENT, LINK_COMMON_HC_ALL)) {
            Expression p0 = mockWithLinkedVariables(va, dv);
            Expression p1 = mockWithLinkedVariables(vb, LINK_COMMON_HC_ALL);
            Expression object = new ExpressionMock();
            MethodCall methodCall = new MethodCall(newId(), object, methodInfo, List.of(p0, p1));
            Expression abc = new StringConstant(primitives, "abc");
            EvaluationResult context = context(evaluationContext(Map.of("p0", abc, "p1", abc)));
            LinkedVariables lv = methodCall.linkedVariables(context);
            assertEquals(1L, lv.stream().count());
            LV result = dv.max(LINK_ASSIGNED);

            // value of 1st parameter, statically_assigned -> assigned
            assertSame(result, lv.value(va.variable()));
        }
    }

    @Test
    @DisplayName("two parameters, mutable object, independence delayed")
    public void test4() {
        minimalMethodAnalysis(false, null, null);
        LinkedVariables lv = twoParameters(mutablePt, LINK_ASSIGNED, LINK_DEPENDENT, LINK_COMMON_HC_ALL);

        assertEquals("a:-1,b:-1,o:-1", lv.toString());
        assertEquals("independent@Method_method", lv.causesOfDelay().toString());
    }


    @Test
    @DisplayName("two parameters, mutable object, independent method")
    public void test5() {
        minimalMethodAnalysis(false, MultiLevel.INDEPENDENT_DV, null);
        LinkedVariables lv = twoParameters(mutablePt, LINK_ASSIGNED, LINK_DEPENDENT, LINK_STATICALLY_ASSIGNED);

        assertEquals("", lv.toString());
        assertTrue(lv.causesOfDelay().isDone());
    }

    @Test
    @DisplayName("two parameters, mutable object, dependent method")
    public void test6() {
        minimalMethodAnalysis(false, MultiLevel.DEPENDENT_DV, SELECT_0);
        LinkedVariables lv = twoParameters(mutablePt, LINK_STATICALLY_ASSIGNED, LINK_DEPENDENT, LINK_DEPENDENT);

        /*
        result = object.method(p1, p2)
        object is linked o:0, a:2, b:4 <0>-<0>
        for example
            o = new ArrayList<T>();
            a = o.sublist(1, 3);
            b = new HashSet<>(o);
            List<T> object = o;
        now 'method' is dependent, e.g.
            List<T> result = object.method(p0, p1);
        we must have
            o:2 (dependent)
            a:2 (still dependent)
            b:4 (with mine still <0>, computed as (*), and theirs equal to mine of the link from object->4->b)

        NOTE: linked variables of parameters are not part of the computation
        (*): the computation is dependent on the method's formal return type, and is stored in
             MethodAnalysis.getHiddenContentSelector()
         */

        assertEquals("a:2,b:2,o:2", lv.toString());
        assertTrue(lv.causesOfDelay().isDone());
    }

    @Test
    @DisplayName("two parameters, recursively immutable object, dependent method")
    public void test7() {
        minimalMethodAnalysis(false, MultiLevel.DEPENDENT_DV, null);
        // values LINK_ASSIGNED should not be present; but they won't be used
        LinkedVariables lv = twoParameters(recursivelyImmutablePt, LINK_ASSIGNED, LINK_ASSIGNED, LINK_ASSIGNED);

        assertTrue(lv.isEmpty());
    }

    @Test
    @DisplayName("two parameters, immutable HC object, dependent method")
    public void test8() {
        minimalMethodAnalysis(false, MultiLevel.DEPENDENT_DV, SELECT_0);
        LV commonHC = LV.createHC(SELECT_0, SELECT_0);
        LinkedVariables lv = twoParameters(immutableHCPt, LINK_STATICALLY_ASSIGNED, LINK_ASSIGNED, commonHC);

        assertEquals("a:4,b:4,o:4", lv.toString());
        assertTrue(lv.causesOfDelay().isDone());
    }

    @Test
    @DisplayName("two parameters, mutable object, method independent HC")
    public void test9() {
        minimalMethodAnalysis(false, MultiLevel.INDEPENDENT_HC_DV, SELECT_0);
        LV commonHC = LV.createHC(SELECT_0, SELECT_0);
        LinkedVariables lv = twoParameters(mutablePt, LINK_STATICALLY_ASSIGNED, LINK_DEPENDENT, commonHC);

        assertEquals("a:4,b:4,o:4", lv.toString());
        assertTrue(lv.causesOfDelay().isDone());
    }

    private LinkedVariables twoParameters(ParameterizedType objectType, LV lvo, LV lva, LV lvb) {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero);
        VariableExpression vb = makeLVAsExpression("b", zero);
        VariableExpression vo = makeLVAsExpression("o", zero, objectType);

        VariableExpression vc = makeLVAsExpression("c", zero);
        VariableExpression vd = makeLVAsExpression("d", zero);

        Expression p0 = mockWithLinkedVariables(vc, LINK_DEPENDENT);
        Expression p1 = mockWithLinkedVariables(vd, LINK_COMMON_HC_ALL);

        Expression object = new ExpressionMock() {
            @Override
            public LinkedVariables linkedVariables(EvaluationResult context) {
                return LinkedVariables.of(Map.of(vo.variable(), lvo, va.variable(), lva, vb.variable(), lvb));
            }

            @Override
            public ParameterizedType returnType() {
                return objectType;
            }
        };

        MethodCall methodCall = new MethodCall(newId(), object, methodInfo, List.of(p0, p1));
        Expression abc = new StringConstant(primitives, "abc");
        EvaluationResult context = context(evaluationContext(Map.of("p0", abc, "p1", abc)));
        return methodCall.linkedVariables(context);
    }
}
