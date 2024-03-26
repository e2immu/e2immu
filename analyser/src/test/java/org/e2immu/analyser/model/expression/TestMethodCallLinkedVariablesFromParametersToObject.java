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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.analyser.LV.CS_ALL;
import static org.e2immu.analyser.analyser.LV.CS_NONE;
import static org.junit.jupiter.api.Assertions.*;

public class TestMethodCallLinkedVariablesFromParametersToObject extends CommonTest {

    /*
    The tests are based on the following example.

    a = some List<T>
    b = some List<T> as the target inside the object o
    c = new ArrayList<>(a)
    d = a.sublist(...)

    o.method(a, c)    p0 links a:1,d:2,c:4;  p1 links c:1,a:4,d:4;  o links b:2

    a, b, c, d are all mutable.
     */
    @Test
    @DisplayName("copy into object on mutable type, o~b.set(p0~a~2d,p1~c~4d), independent delayed on p0")
    public void test1() {
        MethodInfo method = methodWithTwoParameters(null, MultiLevel.INDEPENDENT_DV);

        EvaluationResult er = evaluateCallToMethodWithTwoParameters(method);
        assertEquals("", er.linkedVariablesOfExpression().toString()); // void method
        assertEquals(4, er.changeData().size());
        assertEquals("b:-1", er.findChangeData("a").linkedVariables().toString());
        assertEquals("b:-1", er.findChangeData("c").linkedVariables().toString());
        assertEquals("b:-1", er.findChangeData("d").linkedVariables().toString());

        ChangeData cb = er.findChangeData("b");
        assertEquals("a:-1,c:-1,d:-1", cb.linkedVariables().toString());
        assertEquals("independent@Parameter_p0", cb.linkedVariables().causesOfDelay().toString());
    }

    @Test
    @DisplayName("copy into object on mutable type, o~b.set(p0~a~2d,p1~c~4d), independent delayed x2")
    public void test1a() {
        MethodInfo method = methodWithTwoParameters(null, null);
        EvaluationResult er = evaluateCallToMethodWithTwoParameters(method);
        ChangeData cb = er.findChangeData("b");
        assertEquals("a:-1,c:-1,d:-1", cb.linkedVariables().toString());
        /* we only see the delay of p0 here, because 'independent' is a low priority delay cause; see the
          merge operation in SingleDelay
         */
        assertEquals("independent@Parameter_p0", cb.linkedVariables().causesOfDelay().toString());
    }

    @Test
    @DisplayName("copy into object on mutable type, o~b.set(p0~a~2d,p1~c~4d), dependent x2")
    public void test1e() {
        MethodInfo method = methodWithTwoParameters(MultiLevel.DEPENDENT_DV, MultiLevel.DEPENDENT_DV);
        EvaluationResult er = evaluateCallToMethodWithTwoParameters(method);

        assertEquals(4, er.changeData().size());
        assertEquals("b:2", er.findChangeData("a").linkedVariables().toString());
        assertEquals("b:2", er.findChangeData("c").linkedVariables().toString());
        assertEquals("b:2", er.findChangeData("d").linkedVariables().toString());
        ChangeData cb = er.findChangeData("b");
        // clearly shows the 'min' action at work while merging the two parameters' linked variables.
        assertEquals("a:2,c:2,d:2", cb.linkedVariables().toString());
        assertTrue(cb.linkedVariables().causesOfDelay().isDone());
    }

    @Test
    @DisplayName("copy into object on mutable type, o~b.set(p0~a~2d,p1~c~4d), dependent, independent")
    public void test1b() {
        MethodInfo method = methodWithTwoParameters(MultiLevel.DEPENDENT_DV, MultiLevel.INDEPENDENT_DV);
        EvaluationResult er = evaluateCallToMethodWithTwoParameters(method);

        assertEquals(4, er.changeData().size());
        assertEquals("b:2", er.findChangeData("a").linkedVariables().toString());
        assertEquals("b:4", er.findChangeData("c").linkedVariables().toString());
        assertEquals("b:2", er.findChangeData("d").linkedVariables().toString());
        ChangeData cb = er.findChangeData("b");
        assertEquals("a:2,c:4,d:2", cb.linkedVariables().toString());
        assertTrue(cb.linkedVariables().causesOfDelay().isDone());
    }

    @Test
    @DisplayName("copy into object on mutable type, o~b.set(p0~a~2d,p1~c~4d), independent, dependent")
    public void test1f() {
        MethodInfo method = methodWithTwoParameters(MultiLevel.INDEPENDENT_DV, MultiLevel.DEPENDENT_DV);
        EvaluationResult er = evaluateCallToMethodWithTwoParameters(method);

        assertEquals(4, er.changeData().size());
        assertEquals("b:4", er.findChangeData("a").linkedVariables().toString());
        assertEquals("b:2", er.findChangeData("c").linkedVariables().toString());
        assertEquals("b:4", er.findChangeData("d").linkedVariables().toString());
        ChangeData cb = er.findChangeData("b");
        assertEquals("a:4,c:2,d:4", cb.linkedVariables().toString());
        assertTrue(cb.linkedVariables().causesOfDelay().isDone());
    }


    @Test
    @DisplayName("copy into object on mutable type, o~b.set(p0~a~2d,p1~c~4d), independent x2")
    public void test1c() {
        MethodInfo method = methodWithTwoParameters(MultiLevel.INDEPENDENT_DV, MultiLevel.INDEPENDENT_DV);

        EvaluationResult er = evaluateCallToMethodWithTwoParameters(method);
        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertTrue(er.changeData().isEmpty());
    }

    @Test
    @DisplayName("copy into object on mutable type, o~b.set(p0~a~2d,p1~c~4d), independent hc")
    public void test1d() {
        MethodInfo method2 = methodWithTwoParameters(MultiLevel.INDEPENDENT_HC_DV, MultiLevel.INDEPENDENT_HC_DV);
        EvaluationResult er = evaluateCallToMethodWithTwoParameters(method2);

        assertEquals(4, er.changeData().size());
        assertEquals("b:4", er.findChangeData("a").linkedVariables().toString());
        assertEquals("b:4", er.findChangeData("c").linkedVariables().toString());
        assertEquals("b:4", er.findChangeData("d").linkedVariables().toString());
        ChangeData cb = er.findChangeData("b");
        assertEquals("a:4,c:4,d:4", cb.linkedVariables().toString());
        assertTrue(cb.linkedVariables().causesOfDelay().isDone());
    }

    private EvaluationResult evaluateCallToMethodWithTwoParameters(MethodInfo method) {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero, mutablePtWithOneTypeParameter);
        VariableExpression vb = makeLVAsExpression("b", zero, mutablePtWithOneTypeParameter);
        VariableExpression vc = makeLVAsExpression("c", zero, mutablePtWithOneTypeParameter);
        VariableExpression vd = makeLVAsExpression("d", zero, mutablePtWithOneTypeParameter);

        LV.HiddenContentSelector select0 = LV.selectTypeParameter(0);
        LV hc = LV.createHC(select0, select0);
        assertEquals("<0>-4-<0>", hc.toString());
        ExpressionMock argument0 = simpleMock(mutablePtWithOneTypeParameter, LinkedVariables.of(Map.of(va.variable(),
                LV.LINK_ASSIGNED, vd.variable(), LV.LINK_DEPENDENT, vc.variable(), hc)));
        ExpressionMock argument1 = simpleMock(mutablePtWithOneTypeParameter, LinkedVariables.of(Map.of(vc.variable(),
                LV.LINK_ASSIGNED, va.variable(), hc, vd.variable(), hc)));
        ExpressionMock object = simpleMock(mutablePtWithOneTypeParameter, LinkedVariables.of(vb.variable(),
                LV.LINK_DEPENDENT));

        MethodCall mc = new MethodCall(newId(), object, method, List.of(argument0, argument1));

        EvaluationContext ec = evaluationContext(Map.of("a", va, "b", vb, "c", vc, "d", vd));
        return mc.evaluate(context(ec), ForwardEvaluationInfo.DEFAULT);
    }

    private MethodInfo methodWithTwoParameters(DV independentP0, DV independentP1) {
        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                mutablePt, "p0", 0);
        ParameterInspectionImpl.Builder param1Inspection = new ParameterInspectionImpl.Builder(newId(),
                mutablePt, "p1", 1);

        MethodInfo method = new MethodInspectionImpl.Builder(newId(), primitives.stringTypeInfo(), "method",
                MethodInfo.MethodType.METHOD)
                .setReturnType(primitives.voidParameterizedType())
                .addParameter(param0Inspection)
                .addParameter(param1Inspection)
                .build(inspectionProvider).getMethodInfo();
        TypeAnalysis typeAnalysisOfString = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                primitives.stringTypeInfo(), analyserContext).build();

        LV.HiddenContentSelector select0 = LV.selectTypeParameter(0);
        assertEquals("<0>", select0.toString());

        ParameterAnalysis p0Analysis = parameterAnalysis(0, independentP0, method, select0);
        ParameterAnalysis p1Analysis = parameterAnalysis(1, independentP1, method, select0);

        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, method, typeAnalysisOfString,
                List.of(p0Analysis, p1Analysis));
        builder.setProperty(Property.IDENTITY, DV.FALSE_DV);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        // we're not interested in the return value here! (void method)
        builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
        builder.setHiddenContentSelector(CS_NONE);
        method.setAnalysis(builder.build());

        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false);
        method.methodResolution.set(methodResolution);
        return method;
    }

    private ParameterAnalysis parameterAnalysis(int index, DV independentP0, MethodInfo method, LV.HiddenContentSelector select0) {
        ParameterInfo param0 = method.methodInspection.get().getParameters().get(index);
        ParameterAnalysisImpl.Builder p0Builder = new ParameterAnalysisImpl
                .Builder(primitives, analysisProvider, param0);
        LV.HiddenContentSelector hcs;
        if (independentP0 != null) {
            p0Builder.setProperty(Property.INDEPENDENT, independentP0);
            hcs = independentP0.equals(MultiLevel.INDEPENDENT_HC_DV) ? select0 : CS_NONE;
        } else {
            hcs = CS_NONE;
        }
        p0Builder.setHiddenContentSelector(hcs);
        return (ParameterAnalysis) p0Builder.build();
    }


    @Test
    @DisplayName("b.forEach(e -> a.add(e))")
    public void test2() {
        MethodInfo method = methodWithConsumerParameter(MultiLevel.DEPENDENT_DV, CS_NONE);

        EvaluationResult er = evaluateMethodWithLambdaAsConsumerArgument(method);
        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertEquals(2, er.changeData().size());
        ChangeData ca = er.findChangeData("a");
        assertEquals("b:2", ca.linkedVariables().toString());
        ChangeData cb = er.findChangeData("b");
        assertEquals("a:2", cb.linkedVariables().toString());
        assertTrue(cb.linkedVariables().causesOfDelay().isDone());
    }

    @Test
    @DisplayName("a::add")
    public void test3() {
        MethodInfo add = methodWithHCParameter(MultiLevel.INDEPENDENT_HC_DV, LV.selectTypeParameter(0));

        EvaluationResult er = evaluateMethodReference(add);
        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertEquals(2, er.changeData().size());
        ChangeData ca = er.findChangeData("a");
        assertEquals("p0:4", ca.linkedVariables().toString());
        ChangeData cb = er.findChangeData("p0");
        assertEquals("a:4", cb.linkedVariables().toString());
        assertTrue(cb.linkedVariables().causesOfDelay().isDone());
    }

    @Test
    @DisplayName("b.forEach(a::add)")
    public void test3b() {
        MethodInfo add = methodWithHCParameter(MultiLevel.INDEPENDENT_HC_DV, LV.selectTypeParameter(0));
        MethodInfo forEach = methodWithConsumerParameter(MultiLevel.INDEPENDENT_HC_DV, LV.selectTypeParameter(0));

        EvaluationResult er = evaluateMethodWithMethodReferenceArgument(forEach, add, mutablePtWithOneTypeParameter);

        assertEquals("", er.linkedVariablesOfExpression().toString());
        assertEquals(2, er.changeData().size());
        ChangeData ca = er.findChangeData("a");
        assertEquals("b:4", ca.linkedVariables().toString());
        ChangeData cb = er.findChangeData("b");
        assertEquals("a:4", cb.linkedVariables().toString());
        assertTrue(cb.linkedVariables().causesOfDelay().isDone());
    }


    @Test
    @DisplayName("a::get")
    public void test3c() {
        MethodInfo get = methodReturningHCParameter(MultiLevel.INDEPENDENT_HC_DV, LV.CS_ALL);

        EvaluationResult er = evaluateMethodReference(get);
        assertEquals("a:4", er.linkedVariablesOfExpression().toString());
        assertEquals(0, er.changeData().size());
    }

    /*
       a ~ List<T>
       b ~ Stream<Integer>
       Stream<T> res = b.map(i -> a.get(i)); the result is linked to 'a', not to 'b'
     */
    @Test
    @DisplayName("b.map(a::get)")
    public void test3d() {
        MethodInfo get = methodReturningHCParameter(MultiLevel.INDEPENDENT_HC_DV, LV.CS_ALL);
        ParameterizedType integerPt = primitives.integerTypeInfo().asSimpleParameterizedType();
        MethodInfo map = methodWithFunctionParameter(integerPt, MultiLevel.INDEPENDENT_HC_DV,
                LV.selectTypeParameter(0), tp0Pt, MultiLevel.INDEPENDENT_HC_DV, CS_ALL,
                mutablePtWithOneTypeParameter);
        assertEquals("[com.foo.MutableTP|null]", tp0.getOwner().toString());
        ParameterInfo p0 = map.methodInspection.get().getParameters().get(0);
        assertEquals("Type _internal_.SyntheticFunction1<Integer,T>", p0.parameterizedType.toString());
        assertEquals("com.foo.MutableTP.method(_internal_.SyntheticFunction1<Integer,T>)",
                map.fullyQualifiedName());
        ParameterizedType mutableInteger = new ParameterizedType(mutableWithOneTypeParameter, List.of(integerPt));
        assertEquals("Type com.foo.MutableTP<Integer>", mutableInteger.toString());
        assertEquals("Type com.foo.MutableTP<T>", map.returnType().toString());

        EvaluationResult er = evaluateMethodWithMethodReferenceArgument(map, get, mutableInteger);

        assertEquals("a:4", er.linkedVariablesOfExpression().toString());
        assertEquals(0, er.changeData().size());
    }

    private EvaluationResult evaluateMethodReference(MethodInfo method) {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero, mutablePtWithOneTypeParameter);
        ParameterInfo p0 = method.methodInspection.get().getParameters().get(0);
        ParameterizedType concreteType = method.returnType();
        MethodReference mr = new MethodReference(newId(), va, method, concreteType);
        Expression thisMock = simpleMock(primitives.stringParameterizedType(), LinkedVariables.EMPTY);
        VariableExpression p0Var = new VariableExpression(newId(), p0);
        EvaluationContext ec = evaluationContext(Map.of("this", thisMock, "a", va, "p0", p0Var));
        return mr.evaluate(context(ec), ForwardEvaluationInfo.DEFAULT);
    }

    // b.forEach(a::add)
    private EvaluationResult evaluateMethodWithMethodReferenceArgument(MethodInfo forEach, MethodInfo add,
                                                                       ParameterizedType typeOfB) {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero, mutablePtWithOneTypeParameter);
        ParameterizedType concreteType = add.typeInfo.asParameterizedType(inspectionProvider);
        MethodReference mr = new MethodReference(newId(), va, add, concreteType);

        VariableExpression vb = makeLVAsExpression("b", zero, typeOfB);
        MethodCall mc = new MethodCall(newId(), vb, forEach, List.of(mr));
        Expression thisMock = simpleMock(primitives.stringParameterizedType(), LinkedVariables.EMPTY);

        ParameterInfo p0 = add.methodInspection.get().getParameters().get(0);
        VariableExpression p0Var = new VariableExpression(newId(), p0);

        EvaluationContext ec = evaluationContext(Map.of("this", thisMock, "a", va, "b", vb,
                "p0", p0Var));
        return mc.evaluate(context(ec), ForwardEvaluationInfo.DEFAULT);
    }

    // b.forEach(e -> a.add(e))
    private EvaluationResult evaluateMethodWithLambdaAsConsumerArgument(MethodInfo methodWithConsumerParameter) {
        Expression zero = IntConstant.zero(primitives);
        VariableExpression va = makeLVAsExpression("a", zero, mutablePtWithOneTypeParameter);
        VariableExpression vb = makeLVAsExpression("b", zero, mutablePtWithOneTypeParameter);
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

        MethodCall mc = new MethodCall(newId(), object, methodWithConsumerParameter, List.of(concreteConsumer));
        Expression thisMock = simpleMock(primitives.stringParameterizedType(), LinkedVariables.EMPTY);

        EvaluationContext ec = evaluationContext(Map.of("this", thisMock, "a", va));
        return mc.evaluate(context(ec), ForwardEvaluationInfo.DEFAULT);
    }

    // e.g. boolean List.add(E)
    private MethodInfo methodWithHCParameter(DV independentP0, LV.HiddenContentSelector p0Hcs) {
        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                tp0Pt, "p0", 0);

        MethodInfo method = new MethodInspectionImpl.Builder(newId(), primitives.stringTypeInfo(), "method",
                MethodInfo.MethodType.METHOD)
                .setReturnType(primitives.booleanParameterizedType())
                .addParameter(param0Inspection)
                .build(inspectionProvider).getMethodInfo();
        TypeAnalysis typeAnalysisOfString = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                primitives.stringTypeInfo(), analyserContext).build();

        ParameterAnalysis p0Analysis = parameterAnalysis(0, independentP0, method, p0Hcs);

        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, method, typeAnalysisOfString,
                List.of(p0Analysis));
        builder.setProperty(Property.IDENTITY, DV.FALSE_DV);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        // we're not interested in the return value here! (void method)
        builder.setProperty(Property.INDEPENDENT, MultiLevel.INDEPENDENT_DV);
        builder.setHiddenContentSelector(CS_NONE);
        method.setAnalysis(builder.build());

        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false);
        method.methodResolution.set(methodResolution);
        return method;
    }


    // e.g. T list.get(int)
    private MethodInfo methodReturningHCParameter(DV independent, LV.HiddenContentSelector hcs) {
        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                primitives.intParameterizedType(), "p0", 0);

        MethodInfo method = new MethodInspectionImpl.Builder(newId(), primitives.stringTypeInfo(), "method",
                MethodInfo.MethodType.METHOD)
                .setReturnType(tp0Pt)
                .addParameter(param0Inspection)
                .build(inspectionProvider).getMethodInfo();
        TypeAnalysis typeAnalysisOfString = new TypeAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED, primitives,
                primitives.stringTypeInfo(), analyserContext).build();

        ParameterAnalysis p0Analysis = parameterAnalysis(0, MultiLevel.INDEPENDENT_DV, method, CS_NONE);

        MethodAnalysisImpl.Builder builder = new MethodAnalysisImpl.Builder(Analysis.AnalysisMode.CONTRACTED,
                primitives, analysisProvider, inspectionProvider, method, typeAnalysisOfString,
                List.of(p0Analysis));
        builder.setProperty(Property.IDENTITY, DV.FALSE_DV);
        builder.setProperty(Property.FLUENT, DV.FALSE_DV);
        // we're not interested in the return value here! (void method)
        builder.setProperty(Property.INDEPENDENT, independent);
        builder.setHiddenContentSelector(hcs);
        method.setAnalysis(builder.build());

        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false);
        method.methodResolution.set(methodResolution);
        return method;
    }

    // e.g. forEach(Consumer<E>)
    private MethodInfo methodWithConsumerParameter(DV independentP0, LV.HiddenContentSelector p0Hcs) {
        return methodWithFunctionParameter(tp0Pt, independentP0, p0Hcs, primitives.voidParameterizedType(),
                MultiLevel.INDEPENDENT_DV, CS_NONE, primitives.voidParameterizedType());
    }

    // e.g. map(Function<List<T>,T>), forEach(Consumer<T>)
    private MethodInfo methodWithFunctionParameter(ParameterizedType param0Pt,
                                                   DV independentP0,
                                                   LV.HiddenContentSelector p0Hcs,
                                                   ParameterizedType returnType,
                                                   DV independent,
                                                   LV.HiddenContentSelector hcs,
                                                   ParameterizedType methodReturnType) {
        TypeInfo functionTypeInfo = typeMapBuilder.syntheticFunction(1, returnType.isVoid());
        List<ParameterizedType> typeParamList = returnType.isVoid() ? List.of(param0Pt) :
                List.of(param0Pt, returnType);
        ParameterizedType function = new ParameterizedType(functionTypeInfo, typeParamList);

        ParameterInspectionImpl.Builder param0Inspection = new ParameterInspectionImpl.Builder(newId(),
                function, "p0", 0);

        MethodInfo method = new MethodInspectionImpl.Builder(newId(), mutableWithOneTypeParameter, "method",
                MethodInfo.MethodType.METHOD)
                .setReturnType(methodReturnType)
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
        builder.setProperty(Property.INDEPENDENT, independent);
        builder.setHiddenContentSelector(hcs);
        method.setAnalysis(builder.build());

        MethodResolution methodResolution = new MethodResolution(Set.of(), Set.of(),
                MethodResolution.CallStatus.NON_PRIVATE, true, Set.of(),
                false);
        method.methodResolution.set(methodResolution);
        return method;
    }
}
