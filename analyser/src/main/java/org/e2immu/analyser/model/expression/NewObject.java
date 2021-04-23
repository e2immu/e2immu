/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.model.expression;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/*
 Represents first a newly constructed object, then after applying modifying methods, a "used" object

 */
public record NewObject(
        String identifier, // variable FQN + assignment ID
        MethodInfo constructor,
        ParameterizedType parameterizedType,
        Diamond diamond,
        List<Expression> parameterExpressions,
        int minimalNotNull,
        TypeInfo anonymousClass,
        ArrayInitializer arrayInitializer,
        Expression state) implements HasParameterExpressions {
    // specific construction and copy methods: we explicitly name construction

    /*
    specific situation, new X[] { 0, 1, 2 } array initialiser
     */
    public static Expression withArrayInitialiser(String identifier,
                                                  MethodInfo arrayCreationConstructor,
                                                  ParameterizedType parameterizedType,
                                                  List<Expression> parameterExpressions,
                                                  ArrayInitializer arrayInitializer,
                                                  Expression state) {
        return new NewObject(identifier, arrayCreationConstructor, parameterizedType, Diamond.NO,
                parameterExpressions, MultiLevel.EFFECTIVELY_NOT_NULL, null, arrayInitializer, state);
    }

    public static Expression instanceFromSam(Primitives primitives,
                                             MethodInfo sam,
                                             ParameterizedType parameterizedType) {
        return new NewObject(sam.fullyQualifiedName, null, parameterizedType, Diamond.NO, List.of(),
                MultiLevel.EFFECTIVELY_NOT_NULL, sam.typeInfo, null, new BooleanConstant(primitives, true));
    }

    /*
    used in MethodCall and Field analyser (in the former to enrich with, in the latter to get rid of, state)
     */
    public NewObject copyWithNewState(Expression newState) {
        return new NewObject(identifier, constructor, parameterizedType, diamond, parameterExpressions,
                MultiLevel.EFFECTIVELY_NOT_NULL, anonymousClass, arrayInitializer, newState);
    }

    public NewObject copyAfterModifyingMethodOnConstructor(Expression newState) {
        return new NewObject(identifier, null, parameterizedType, diamond, List.of(),
                MultiLevel.EFFECTIVELY_NOT_NULL, null, null,
                newState);
    }

    public static NewObject forTesting(Primitives primitives, ParameterizedType parameterizedType) {
        return new NewObject("-", null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                MultiLevel.EFFECTIVELY_NOT_NULL, null, null,
                new BooleanConstant(primitives, true));
    }

    // never null, never more interesting.
    public static NewObject forCatchOrThis(String identifier,
                                           Primitives primitives, ParameterizedType parameterizedType) {
        Diamond diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : Diamond.SHOW_ALL;
        return new NewObject(identifier, null, parameterizedType, diamond, List.of(), MultiLevel.EFFECTIVELY_NOT_NULL,
                null, null,
                new BooleanConstant(primitives, true));
    }

    /*
     local variable, defined outside a loop, will be assigned inside the loop
     don't assume that this instance is non-null straight away; state is also generic at this point
     */

    public static NewObject localVariableInLoop(String identifier,
                                                Primitives primitives, ParameterizedType parameterizedType) {
        return new NewObject(identifier, null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                parameterizedType.defaultNotNull(), null, null,
                new BooleanConstant(primitives, true));
    }

    public static NewObject localCopyOfVariableField(String identifier,
                                                     Primitives primitives, ParameterizedType parameterizedType) {
        return new NewObject(identifier, null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                parameterizedType.defaultNotNull(), null, null,
                new BooleanConstant(primitives, true));
    }

    /*
    not-null always in properties
     */
    public static NewObject initialValueOfParameter(String identifier,
                                                    ParameterizedType parameterizedType,
                                                    Expression state,
                                                    int contractNotNull) {
        return new NewObject(identifier, null, parameterizedType, Diamond.SHOW_ALL, List.of(), contractNotNull,
                null, null, state);
    }

    public static NewObject initialValueOfFieldPartOfConstruction(String identifier,
                                                                  EvaluationContext evaluationContext,
                                                                  FieldReference fieldReference) {
        int notNull = evaluationContext.getProperty(fieldReference, VariableProperty.NOT_NULL_EXPRESSION);
        return new NewObject(identifier, null, fieldReference.parameterizedType(), Diamond.SHOW_ALL, List.of(),
                notNull, null, null,
                new BooleanConstant(evaluationContext.getPrimitives(), true));
    }

    /* like a local variable in loop*/
    public static NewObject initialValueOfField(String identifier,
                                                Primitives primitives,
                                                ParameterizedType parameterizedType) {
        return new NewObject(identifier, null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                parameterizedType.defaultNotNull(), null, null,
                new BooleanConstant(primitives, true));
    }

    /* like a local variable in loop*/
    public static NewObject initialValueOfExternalField(String identifier,
                                                        Primitives primitives,
                                                        ParameterizedType parameterizedType,
                                                        int minimalNotNull) {
        return new NewObject(identifier, null, parameterizedType, Diamond.SHOW_ALL, List.of(), minimalNotNull,
                null, null, new BooleanConstant(primitives, true));
    }

    // null-status derived from variable in evaluation context
    public static NewObject genericMergeResult(String identifier,
                                               Primitives primitives, VariableInfo variableInfo) {
        int notNull = MultiLevel.bestNotNull(variableInfo.getProperty(VariableProperty.NOT_NULL_EXPRESSION),
                variableInfo.variable().parameterizedType().defaultNotNull());
        return new NewObject(identifier, null, variableInfo.variable().parameterizedType(),
                Diamond.SHOW_ALL, List.of(), notNull, null, null,
                new BooleanConstant(primitives, true));
    }

    // null-status derived from variable in evaluation context
    public static NewObject genericMergeResult(String identifier,
                                               Primitives primitives, VariableInfo variableInfo, int notNull) {
        return new NewObject(identifier, null, variableInfo.variable().parameterizedType(),
                Diamond.SHOW_ALL, List.of(), notNull, null, null,
                new BooleanConstant(primitives, true));
    }

    public static Expression genericArrayAccess(String identifier,
                                                EvaluationContext evaluationContext,
                                                Expression array,
                                                Variable variable) {
        int notNull = evaluationContext.getProperty(array, VariableProperty.NOT_NULL_EXPRESSION, true, false);
        if (notNull == Level.DELAY) return DelayedExpression.forNewObject(variable.parameterizedType());
        return new NewObject(identifier, null, variable.parameterizedType(), Diamond.SHOW_ALL, List.of(), notNull,
                null, null,
                new BooleanConstant(evaluationContext.getPrimitives(), true));
    }

    /*
    When creating an anonymous instance of a class (new SomeType() { })
     */
    public static NewObject withAnonymousClass(String identifier,
                                               Primitives primitives,
                                               @NotNull ParameterizedType parameterizedType,
                                               @NotNull TypeInfo anonymousClass,
                                               Diamond diamond) {
        return new NewObject(identifier, null, parameterizedType, diamond,
                List.of(), MultiLevel.EFFECTIVELY_NOT_NULL, anonymousClass, null,
                new BooleanConstant(primitives, true));
    }

    /*
    getInstance is used by MethodCall to enrich an instance with state.

    cannot be null, we're applying a method on it.
     */
    public static NewObject forGetInstance(String identifier,
                                           Primitives primitives,
                                           ParameterizedType parameterizedType) {
        return new NewObject(identifier, null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                MultiLevel.EFFECTIVELY_NOT_NULL, null, null,
                new BooleanConstant(primitives, true));
    }

    /*
    getInstance is used by MethodCall to enrich an instance with state.

    2nd version, one with known state, used by EvaluationResult.currentInstance
    cannot be null, we're applying a method on it.
    */
    public static NewObject forGetInstance(String identifier,
                                           ParameterizedType parameterizedType, Expression state,
                                           int minimalNotNull) {
        return new NewObject(identifier, null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                minimalNotNull, null, null, state);
    }

    /*
    Result of actual object creation expressions (new XX, xx::new, ...)
     */
    public static NewObject objectCreation(String identifier,
                                           Primitives primitives,
                                           MethodInfo constructor,
                                           ParameterizedType parameterizedType,
                                           Diamond diamond,
                                           List<Expression> parameterExpressions) {
        return new NewObject(identifier, constructor, parameterizedType, diamond, parameterExpressions, MultiLevel.EFFECTIVELY_NOT_NULL,
                null, null, new BooleanConstant(primitives, true));
    }

    public NewObject(String identifier,
                     MethodInfo constructor,
                     ParameterizedType parameterizedType,
                     Diamond diamond,
                     List<Expression> parameterExpressions,
                     int minimalNotNull,
                     TypeInfo anonymousClass,
                     ArrayInitializer arrayInitializer,
                     Expression state) {
        this.identifier = identifier;
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.constructor = constructor; // can be null after modification (constructor lost)
        this.anonymousClass = anonymousClass;
        this.arrayInitializer = arrayInitializer;
        this.state = Objects.requireNonNull(state);
        this.minimalNotNull = minimalNotNull;
        assert minimalNotNull != Level.DELAY;
        this.diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : diamond;
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
        return identifier.equals(newObject.identifier) &&
                parameterizedType.equals(newObject.parameterizedType) &&
                parameterExpressions.equals(newObject.parameterExpressions) &&
                Objects.equals(anonymousClass, newObject.anonymousClass) &&
                Objects.equals(constructor, newObject.constructor) &&
                Objects.equals(arrayInitializer, newObject.arrayInitializer) &&
                Objects.equals(state, newObject.state) &&
                minimalNotNull == newObject.minimalNotNull;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, parameterExpressions, anonymousClass, constructor,
                arrayInitializer, state, minimalNotNull);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new NewObject(identifier,
                constructor,
                translationMap.translateType(parameterizedType),
                diamond,
                parameterExpressions.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                minimalNotNull,
                anonymousClass, // not translating this yet!
                arrayInitializer == null ? null : TranslationMap.ensureExpressionType(arrayInitializer, ArrayInitializer.class),
                state);
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
     * 1. no explicit constructor, no parameters: independent (static or not, doesn't matter)
     * 2. constructor is @Independent: independent
     * 3. B is @E2Immutable: independent
     *
     * the default case is a dependence on c and d
     */
    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        // RULE 1
        if (constructor == null) return LinkedVariables.EMPTY;
        if (parameterExpressions.isEmpty()) {
            return LinkedVariables.EMPTY;
        }

        // RULE 2, 3
        boolean notSelf = constructor.typeInfo != evaluationContext.getCurrentType();
        if (notSelf) {
            TypeAnalysis typeAnalysisOfConstructor = evaluationContext.getAnalyserContext()
                    .getTypeAnalysis(constructor.typeInfo);
            int immutable = typeAnalysisOfConstructor.getProperty(VariableProperty.IMMUTABLE);
            int typeIndependent = typeAnalysisOfConstructor.getProperty(VariableProperty.INDEPENDENT);
            MethodAnalysis methodAnalysisOfConstructor = evaluationContext.getAnalyserContext()
                    .getMethodAnalysis(constructor);
            int independent = methodAnalysisOfConstructor.getProperty(VariableProperty.INDEPENDENT);

            if (MultiLevel.isAtLeastEventuallyE2Immutable(immutable) || independent == MultiLevel.EFFECTIVE
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
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty, boolean duringEvaluation) {
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
            case PROPAGATE_MODIFICATION_DELAY:
            case IDENTITY:
            case IGNORE_MODIFICATIONS:
                return Level.FALSE;

            case INDEPENDENT: // meant for DEP1, DEP2
                return Level.DELAY;

            case CONTAINER: { // must be pretty similar to the code in ParameterAnalysis, because every parameter will be of this type
                Boolean implicit = parameterizedType.isImplicitlyImmutable(evaluationContext.getAnalyserContext(),
                        evaluationContext.getCurrentType());
                if (implicit == Boolean.TRUE) return Level.TRUE;
                // if implicit is null, we cannot return FALSE, we'll have to wait!
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                int withoutImplicitDelay;
                if (bestType != null) {
                    withoutImplicitDelay = evaluationContext.getAnalyserContext()
                            .getTypeAnalysis(bestType).getProperty(VariableProperty.CONTAINER);
                } else {
                    withoutImplicitDelay = Level.FALSE;
                }
                return implicit == null && withoutImplicitDelay != Level.TRUE ? Level.DELAY : withoutImplicitDelay;
            }
            case IMMUTABLE: {
                int immutable = parameterizedType.defaultImmutable(evaluationContext.getAnalyserContext());
                if (constructor != null) {
                    if (immutable == MultiLevel.EVENTUALLY_E1IMMUTABLE)
                        return MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK;
                    if (immutable == MultiLevel.EVENTUALLY_E2IMMUTABLE)
                        return MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK;
                }
                return immutable;
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
        if (constructor != null || anonymousClass != null) {
            outputBuilder.add(new Text("new")).add(Space.ONE)
                    .add(parameterizedType.output(qualification, false, diamond));
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
            outputBuilder.add(anonymousClass.output(qualification, false));
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
            builder.setExpression(new ArrayInitializer(evaluationContext.getAnalyserContext(), values, arrayInitializer.returnType()));
            return builder.build();
        }

        // "normal"

        Pair<EvaluationResult.Builder, List<Expression>> res = EvaluateParameters.transform(parameterExpressions,
                evaluationContext, constructor, Level.FALSE, false, null);

        ParameterizedType pt;
        if (anonymousClass != null) {
            pt = anonymousClass.asParameterizedType(evaluationContext.getAnalyserContext());
        } else {
            pt = parameterizedType;
        }
        NewObject initialInstance = NewObject.objectCreation(identifier, evaluationContext.getPrimitives(),
                constructor, pt, diamond, res.v);
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

        int immutable = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_IMMUTABLE);
        if (MultiLevel.isAfter(immutable)) {
            res.k.raiseError(Message.Label.EVENTUAL_AFTER_REQUIRED);
        }
        return res.k.build();
    }

    public Expression stateTranslateThisTo(FieldReference fieldReference) {
        if (state.isBooleanConstant()) return state;
        // the "this" in the state can belong to the type of the object, or any of its super types
        This thisVar = findThis();
        return state.translate(new TranslationMap.TranslationMapBuilder().put(thisVar, fieldReference).build());
    }

    private This findThis() {
        AtomicReference<This> thisVar = new AtomicReference<>();
        state.visit(e -> {
            if (e instanceof VariableExpression ve && ve.variable() instanceof This tv) {
                thisVar.set(tv);
                return false;
            }
            return true;
        });
        return thisVar.get();
    }
}
