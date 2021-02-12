/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/*
 Represents first a newly constructed object, then after applying modifying methods, a "used" object

 */
public record NewObject(MethodInfo constructor,
                        ParameterizedType parameterizedType,
                        Diamond diamond,
                        List<Expression> parameterExpressions,
                        int minimalNotNull,
                        TypeInfo anonymousClass,
                        ArrayInitializer arrayInitializer,
                        Expression state,
                        ObjectFlow objectFlow) implements HasParameterExpressions {
    // specific construction and copy methods: we explicitly name construction

    /*
    specific situation, new X[] { 0, 1, 2 } array initialiser
     */
    public static Expression withArrayInitialiser(MethodInfo arrayCreationConstructor,
                                                  ParameterizedType parameterizedType,
                                                  List<Expression> parameterExpressions,
                                                  ArrayInitializer arrayInitializer,
                                                  Expression state,
                                                  ObjectFlow objectFlow) {
        return new NewObject(arrayCreationConstructor, parameterizedType, Diamond.NO,
                parameterExpressions, MultiLevel.EFFECTIVELY_NOT_NULL, null, arrayInitializer, state, objectFlow);
    }

    /*
    used in MethodCall and Field analyser (in the former to enrich with, in the latter to get rid of, state)
     */
    public NewObject copyWithNewState(Expression newState) {
        return new NewObject(constructor, parameterizedType, diamond, parameterExpressions, MultiLevel.EFFECTIVELY_NOT_NULL,
                anonymousClass, arrayInitializer, newState, objectFlow);
    }

    public NewObject copyAfterModifyingMethodOnConstructor(Expression newState) {
        return new NewObject(null, parameterizedType, diamond, List.of(), MultiLevel.EFFECTIVELY_NOT_NULL, null, null,
                newState, getObjectFlow());
    }

    public static NewObject forTesting(Primitives primitives, ParameterizedType parameterizedType) {
        return new NewObject(null, parameterizedType, Diamond.SHOW_ALL, List.of(), MultiLevel.EFFECTIVELY_NOT_NULL, null, null,
                new BooleanConstant(primitives, true), ObjectFlow.NO_FLOW);
    }

    // never null, never more interesting.
    public static NewObject forCatchOrThis(Primitives primitives, ParameterizedType parameterizedType) {
        Diamond diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : Diamond.SHOW_ALL;
        return new NewObject(null, parameterizedType, diamond, List.of(), MultiLevel.EFFECTIVELY_NOT_NULL, null, null,
                new BooleanConstant(primitives, true), ObjectFlow.NO_FLOW);
    }

    public static NewObject forForEach(Primitives primitives, ParameterizedType parameterizedType, boolean notNull) {
        Diamond diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : Diamond.SHOW_ALL;
        return new NewObject(null, parameterizedType, diamond, List.of(),
                notNull ? MultiLevel.EFFECTIVELY_NOT_NULL : MultiLevel.NULLABLE, null, null,
                new BooleanConstant(primitives, true), ObjectFlow.NO_FLOW);
    }

    /*
     local variable, defined outside a loop, will be assigned inside the loop
     don't assume that this instance is non-null straight away; state is also generic at this point
     */

    public static NewObject localVariableInLoop(Primitives primitives, ParameterizedType parameterizedType) {
        return new NewObject(null, parameterizedType, Diamond.SHOW_ALL, List.of(), Level.DELAY, null, null,
                new BooleanConstant(primitives, true), ObjectFlow.NO_FLOW);
    }

    public static NewObject localCopyOfVariableField(Primitives primitives, ParameterizedType parameterizedType, ObjectFlow objectFlow) {
        return new NewObject(null, parameterizedType, Diamond.SHOW_ALL, List.of(), Level.DELAY, null, null,
                new BooleanConstant(primitives, true), objectFlow);
    }

    /*
    not-null always in properties
     */
    public static NewObject initialValueOfParameter(ParameterizedType parameterizedType, Expression state, ObjectFlow objectFlow) {
        return new NewObject(null, parameterizedType, Diamond.SHOW_ALL, List.of(), Level.DELAY, null, null,
                state, objectFlow);
    }

    public static NewObject initialValueOfFieldPartOfConstruction(EvaluationContext evaluationContext, FieldReference fieldReference, ObjectFlow objectFlow) {
        int notNull = evaluationContext.getProperty(fieldReference, VariableProperty.NOT_NULL_EXPRESSION);
        return new NewObject(null, fieldReference.parameterizedType(), Diamond.SHOW_ALL, List.of(), notNull, null, null,
                new BooleanConstant(evaluationContext.getPrimitives(), true), objectFlow);
    }

    /* like a local variable in loop*/
    public static NewObject initialValueOfField(Primitives primitives, ParameterizedType parameterizedType, ObjectFlow objectFlow) {
        return new NewObject(null, parameterizedType, Diamond.SHOW_ALL, List.of(), Level.DELAY, null, null,
                new BooleanConstant(primitives, true), objectFlow);
    }

    /* like a local variable in loop*/
    public static NewObject initialValueOfExternalField(Primitives primitives,
                                                        ParameterizedType parameterizedType,
                                                        int minimalNotNull,
                                                        ObjectFlow objectFlow) {
        return new NewObject(null, parameterizedType, Diamond.SHOW_ALL, List.of(), minimalNotNull, null, null,
                new BooleanConstant(primitives, true), objectFlow);
    }

    // null-status derived from variable in evaluation context
    public static NewObject genericMergeResult(Primitives primitives, VariableInfo variableInfo) {
        return new NewObject(null, variableInfo.variable().parameterizedType(),
                Diamond.SHOW_ALL, List.of(), variableInfo.getProperty(VariableProperty.NOT_NULL_VARIABLE), null, null,
                new BooleanConstant(primitives, true), variableInfo.getObjectFlow());
    }

    public static NewObject genericArrayAccess(EvaluationContext evaluationContext, Expression array, Variable variable, ObjectFlow objectFlow) {
        int notNull = evaluationContext.getProperty(array, VariableProperty.NOT_NULL_VARIABLE);
        return new NewObject(null, variable.parameterizedType(), Diamond.SHOW_ALL, List.of(), notNull, null, null,
                new BooleanConstant(evaluationContext.getPrimitives(), true), objectFlow);
    }

    /*
    When creating an anonymous instance of a class (new SomeType() { })
     */
    public static NewObject withAnonymousClass(Primitives primitives,
                                               @NotNull ParameterizedType parameterizedType,
                                               @NotNull TypeInfo anonymousClass,
                                               Diamond diamond) {
        return new NewObject(null, parameterizedType, diamond,
                List.of(), MultiLevel.EFFECTIVELY_NOT_NULL, anonymousClass, null,
                new BooleanConstant(primitives, true), ObjectFlow.NO_FLOW);
    }

    /*
    getInstance is used by MethodCall to enrich an instance with state.

    cannot be null, we're applying a method on it.
     */
    public static NewObject forGetInstance(Primitives primitives, ParameterizedType parameterizedType, ObjectFlow objectFlow) {
        return new NewObject(null, parameterizedType, Diamond.SHOW_ALL, List.of(), MultiLevel.EFFECTIVELY_NOT_NULL, null, null,
                new BooleanConstant(primitives, true), objectFlow);
    }

    /*
    getInstance is used by MethodCall to enrich an instance with state.

    2nd version, one with known state, used by EvaluationResult.currentInstance
    cannot be null, we're applying a method on it.
    */
    public static NewObject forGetInstance(ParameterizedType parameterizedType, Expression state, ObjectFlow objectFlow) {
        return new NewObject(null, parameterizedType, Diamond.SHOW_ALL, List.of(), MultiLevel.EFFECTIVELY_NOT_NULL, null, null,
                state, objectFlow);
    }

    /*
    Result of actual object creation expressions (new XX, xx::new, ...)
     */
    public static NewObject objectCreation(Primitives primitives,
                                           MethodInfo constructor,
                                           ParameterizedType parameterizedType,
                                           Diamond diamond,
                                           List<Expression> parameterExpressions,
                                           ObjectFlow objectFlow) {
        assert diamond == Diamond.NO || !parameterizedType.parameters.isEmpty();

        return new NewObject(constructor, parameterizedType, diamond, parameterExpressions, MultiLevel.EFFECTIVELY_NOT_NULL,
                null, null, new BooleanConstant(primitives, true), objectFlow);
    }

    public NewObject(MethodInfo constructor,
                     ParameterizedType parameterizedType,
                     Diamond diamond,
                     List<Expression> parameterExpressions,
                     int minimalNotNull,
                     TypeInfo anonymousClass,
                     ArrayInitializer arrayInitializer,
                     Expression state,
                     ObjectFlow objectFlow) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.constructor = constructor; // can be null after modification (constructor lost)
        this.anonymousClass = anonymousClass;
        this.arrayInitializer = arrayInitializer;
        this.state = Objects.requireNonNull(state);
        this.objectFlow = Objects.requireNonNull(objectFlow);
        this.minimalNotNull = minimalNotNull;
        this.diamond = diamond;
        assert !(constructor != null && minimalNotNull < MultiLevel.EFFECTIVELY_NOT_NULL);
    }

    @Override
    public TypeInfo definesType() {
        return anonymousClass;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NewObject newObject = (NewObject) o;
        return parameterizedType.equals(newObject.parameterizedType) &&
                parameterExpressions.equals(newObject.parameterExpressions) &&
                Objects.equals(anonymousClass, newObject.anonymousClass) &&
                Objects.equals(constructor, newObject.constructor) &&
                Objects.equals(arrayInitializer, newObject.arrayInitializer) &&
                Objects.equals(state, newObject.state) &&
                minimalNotNull == newObject.minimalNotNull;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, parameterExpressions, anonymousClass, constructor, arrayInitializer, state, minimalNotNull);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new NewObject(constructor,
                translationMap.translateType(parameterizedType),
                diamond,
                parameterExpressions.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                minimalNotNull,
                anonymousClass, // not translating this yet!
                TranslationMap.ensureExpressionType(arrayInitializer, ArrayInitializer.class),
                state, objectFlow);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INSTANCE;
    }


    @Override
    public String toString() {
        return minimalOutput();
    }

    /*
     * Rules, assuming the notation b = new B(c, d)
     *
     * 1. no explicit constructor, no parameters on a static type: independent
     * 2. constructor is @Independent: independent
     * 3. B is @E2Immutable: independent
     *
     * the default case is a dependence on c and d
     */
    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        // RULE 1
        if (constructor == null) return LinkedVariables.EMPTY;
        if (parameterExpressions.isEmpty() && constructor.typeInfo.isStatic()) {
            return LinkedVariables.EMPTY;
        }

        // RULE 2, 3
        boolean notSelf = constructor.typeInfo != evaluationContext.getCurrentType();
        if (notSelf) {
            TypeAnalysis typeAnalysisOfConstructor = evaluationContext.getAnalyserContext().getTypeAnalysis(constructor.typeInfo);
            int immutable = typeAnalysisOfConstructor.getProperty(VariableProperty.IMMUTABLE);
            int typeIndependent = typeAnalysisOfConstructor.getProperty(VariableProperty.INDEPENDENT);
            MethodAnalysis methodAnalysisOfConstructor = evaluationContext.getAnalyserContext().getMethodAnalysis(constructor);
            int independent = methodAnalysisOfConstructor.getProperty(VariableProperty.INDEPENDENT);

            if (MultiLevel.isE2Immutable(immutable) || independent == MultiLevel.EFFECTIVE
                    || typeIndependent == MultiLevel.EFFECTIVE) { // RULE 3
                return LinkedVariables.EMPTY;
            }
            if (independent == Level.DELAY) return LinkedVariables.DELAY;
            if (immutable == MultiLevel.DELAY) return LinkedVariables.DELAY;
            if (typeIndependent == MultiLevel.DELAY) return LinkedVariables.DELAY;
        }

        // default case
        Set<Variable> result = new HashSet<>();
        for (Expression value : parameterExpressions) {
            LinkedVariables sub = evaluationContext.linkedVariables(value);
            if (sub == LinkedVariables.DELAY) return LinkedVariables.DELAY;
            result.addAll(sub.variables());
        }
        return new LinkedVariables(result);
    }

    @Override
    public int internalCompareTo(Expression v) {
        return parameterizedType.detailedString()
                .compareTo(((NewObject) v).parameterizedType.detailedString());
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationContext) {
        return this;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_NULL_EXPRESSION: {
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                if (Primitives.isPrimitiveExcludingVoid(bestType))
                    return MultiLevel.EFFECTIVELY_NOT_NULL;
                return minimalNotNull;
            }
            case CONTEXT_MODIFIED:
            case NOT_MODIFIED_1:
            case CONTEXT_MODIFIED_DELAY:
            case IDENTITY:
            case IGNORE_MODIFICATIONS:
                return Level.FALSE;

            case IMMUTABLE:
            case CONTAINER: {
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                if (Primitives.isPrimitiveExcludingVoid(bestType)) return variableProperty.best;
                return bestType == null ? variableProperty.falseValue :
                        evaluationContext.getAnalyserContext().getTypeAnalysis(bestType).getProperty(variableProperty);
            }
            default:
        }
        // @NotModified should not be asked here
        throw new UnsupportedOperationException("Asking for " + variableProperty);
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            parameterExpressions.forEach(predicate::test);
        }
    }

    @Override
    public boolean isNumeric() {
        return parameterizedType.isType() && Primitives.isNumeric(parameterizedType.typeInfo);
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public MethodInfo getMethodInfo() {
        return constructor;
    }

    @Override
    public List<Expression> getParameterExpressions() {
        return parameterExpressions;
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (constructor != null) {
            outputBuilder.add(new Text("new")).add(Space.ONE).add(parameterizedType.output(qualification, false, diamond));
            if (arrayInitializer == null) {
                if (parameterExpressions.isEmpty()) {
                    outputBuilder.add(Symbol.OPEN_CLOSE_PARENTHESIS);
                } else {
                    outputBuilder
                            .add(Symbol.LEFT_PARENTHESIS)
                            .add(parameterExpressions.stream().map(expression -> expression.output(qualification))
                                    .collect(OutputBuilder.joining(Symbol.COMMA)))
                            .add(Symbol.RIGHT_PARENTHESIS);
                }
            }
        } else {
            Text text = new Text(text() + "instance type " + parameterizedType.printSimple());
            outputBuilder.add(text);
        }
        if (anonymousClass != null) {
            outputBuilder.add(anonymousClass.output(qualification, true));
        }
        if (arrayInitializer != null) {
            outputBuilder.add(arrayInitializer.output(qualification));
        }
        if (!state.isBoolValueTrue()) {
            outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT).add(state.output(qualification)).add(Symbol.RIGHT_BLOCK_COMMENT);
        }
        return outputBuilder;
    }

    private String text() {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (Primitives.isPrimitiveExcludingVoid(bestType)) return "";
        if (minimalNotNull == Level.DELAY) return "nullable? ";
        if (minimalNotNull < MultiLevel.EFFECTIVELY_NOT_NULL) return "nullable ";
        return "";
    }

    @Override
    public Precedence precedence() {
        return Precedence.UNARY;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                parameterizedType.typesReferenced(true),
                parameterExpressions.stream().flatMap(e -> e.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()));
    }

    @Override
    public List<? extends Element> subElements() {
        return parameterExpressions;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {

        // arrayInitializer variant

        if (arrayInitializer != null) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
            List<EvaluationResult> results = arrayInitializer.multiExpression.stream()
                    .map(e -> e.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT))
                    .collect(Collectors.toList());
            builder.compose(results);
            List<Expression> values = results.stream().map(EvaluationResult::getExpression).collect(Collectors.toList());
            ObjectFlow objectFlow = builder.createLiteralObjectFlow(arrayInitializer.returnType());
            builder.setExpression(new ArrayInitializer(evaluationContext.getPrimitives(),
                    objectFlow, values, arrayInitializer.returnType()));
            return builder.build();
        }

        // "normal"

        Pair<EvaluationResult.Builder, List<Expression>> res = EvaluateParameters.transform(parameterExpressions,
                evaluationContext, constructor, Level.FALSE, null);
        Location location = evaluationContext.getLocation(this);
        ObjectFlow objectFlow = res.k.createInternalObjectFlow(location, parameterizedType, Origin.NEW_OBJECT_CREATION);

        NewObject initialInstance = NewObject.objectCreation(evaluationContext.getPrimitives(),
                constructor, parameterizedType, diamond, res.v, objectFlow);
        Expression instance;
        if (constructor != null) {
            // check state changes of companion methods
            MethodAnalysis constructorAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(constructor);
            NewObject no = MethodCall.checkCompanionMethodsModifying(res.k, evaluationContext, constructor, constructorAnalysis,
                    null, initialInstance, res.v);
            instance = no == null ? DelayedExpression.forNewObject(parameterizedType) : no;
        } else {
            instance = initialInstance;
        }
        res.k.setExpression(instance);

        if (constructor != null &&
                (!constructor.methodResolution.isSet() || constructor.methodResolution.get().allowsInterrupts())) {
            res.k.incrementStatementTime();
        }

        if (anonymousClass != null) {
            evaluationContext.getLocalPrimaryTypeAnalysers().stream()
                    .filter(pta -> pta.primaryType == anonymousClass)
                    .forEach(res.k::markVariablesFromPrimaryTypeAnalyser);
        }

        return res.k.build();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        SideEffect params = parameterExpressions.stream()
                .map(e -> e.sideEffect(evaluationContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);

        if (constructor != null) {
            int modified = constructor.atLeastOneParameterModified();
            if (modified == Level.FALSE && params.lessThan(SideEffect.SIDE_EFFECT)) {
                return SideEffect.STATIC_ONLY;
            }
            if (modified == Level.DELAY) return SideEffect.DELAYED;
        }

        return params;
    }


}
