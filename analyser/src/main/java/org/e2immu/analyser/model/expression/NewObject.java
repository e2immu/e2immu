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
import org.e2immu.analyser.analyser.util.DelayDebugger;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 Represents first a newly constructed object, then after applying modifying methods, a "used" object

 */
public record NewObject(
        Identifier identifier, // variable FQN + assignment ID
        MethodInfo constructor,
        ParameterizedType parameterizedType,
        Diamond diamond,
        List<Expression> parameterExpressions,
        int minimalNotNull,
        boolean identity,
        TypeInfo anonymousClass,
        ArrayInitializer arrayInitializer) implements HasParameterExpressions {
    // specific construction and copy methods: we explicitly name construction

    /*
    specific situation, new X[] { 0, 1, 2 } array initialiser
     */
    public static Expression withArrayInitialiser(MethodInfo arrayCreationConstructor,
                                                  ParameterizedType parameterizedType,
                                                  List<Expression> parameterExpressions,
                                                  ArrayInitializer arrayInitializer) {
        return new NewObject(Identifier.generate(), arrayCreationConstructor, parameterizedType, Diamond.NO,
                parameterExpressions, MultiLevel.EFFECTIVELY_NOT_NULL, false,
                null, arrayInitializer);
    }

    public static Expression forUnspecifiedLoopCondition(String index, Primitives primitives) {
        return new NewObject(Identifier.loopCondition(index), null, primitives.booleanParameterizedType, Diamond.NO, List.of(),
                MultiLevel.EFFECTIVELY_NOT_NULL, false, null, null);
    }

    public static Expression instanceFromSam(MethodInfo sam, ParameterizedType parameterizedType) {
        return new NewObject(sam.getIdentifier(), null, parameterizedType, Diamond.NO, List.of(),
                MultiLevel.EFFECTIVELY_NOT_NULL, false,
                sam.typeInfo, null);
    }

    public static Expression genericFieldAccess(InspectionProvider inspectionProvider, FieldInfo fieldInfo) {
        return new NewObject(Identifier.generate(), null,
                fieldInfo.owner.asParameterizedType(inspectionProvider), Diamond.NO, List.of(),
                MultiLevel.EFFECTIVELY_NOT_NULL, false, null, null);
    }

    public static NewObject forInlinedMethod(Identifier identifier,
                                             ParameterizedType parameterizedType,
                                             int notNull) {
        return new NewObject(identifier, null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                notNull, false, null, null);
    }

    public static Expression forMethodResult(Identifier identifier,
                                             ParameterizedType parameterizedType,
                                             int notNull) {
        return new NewObject(identifier, null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                notNull, false, null, null);
    }

    public static Expression forUnspecifiedCatchCondition(String index, Primitives primitives) {
        return new NewObject(Identifier.catchCondition(index),
                null, primitives.booleanParameterizedType, Diamond.NO, List.of(),
                MultiLevel.EFFECTIVELY_NOT_NULL, false, null, null);
    }

    public static NewObject forTesting(ParameterizedType parameterizedType) {
        return new NewObject(Identifier.generate(), null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                MultiLevel.EFFECTIVELY_NOT_NULL, false, null, null);
    }

    // never null, never more interesting.
    public static NewObject forCatchOrThis(String index, Variable variable) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        Diamond diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : Diamond.SHOW_ALL;
        return new NewObject(Identifier.variable(variable, index),
                null, parameterizedType, diamond, List.of(), MultiLevel.EFFECTIVELY_NOT_NULL,
                false, null, null);
    }

    // never null, never more interesting.
    public static NewObject forLoopVariable(String index, Variable variable, int initialNotNull) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        Diamond diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : Diamond.SHOW_ALL;
        return new NewObject(Identifier.variable(variable, index),
                null, parameterizedType, diamond, List.of(), initialNotNull,
                false, null, null);
    }
    /*
     local variable, defined outside a loop, will be assigned inside the loop
     don't assume that this instance is non-null straight away; state is also generic at this point
     */

    public static NewObject localVariableInLoop(String index, Variable variable) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        return new NewObject(Identifier.variable(variable, index), null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                parameterizedType.defaultNotNull(), false, null, null);
    }

    public static NewObject localCopyOfVariableField(String index, Variable variable) {
        ParameterizedType parameterizedType = variable.parameterizedType();
        return new NewObject(Identifier.variable(variable, index), null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                parameterizedType.defaultNotNull(), false, null, null);
    }

    /*
    not-null always in properties
     */
    public static NewObject initialValueOfParameter(ParameterInfo parameterInfo,
                                                    int contractNotNull,
                                                    boolean identity) {
        return new NewObject(Identifier.variable(parameterInfo), null, parameterInfo.parameterizedType,
                Diamond.SHOW_ALL, List.of(), contractNotNull,
                identity, null, null);
    }

    public static NewObject initialValueOfFieldPartOfConstruction(String index,
                                                                  EvaluationContext evaluationContext,
                                                                  FieldReference fieldReference) {
        int notNull = evaluationContext.getProperty(fieldReference, VariableProperty.NOT_NULL_EXPRESSION);
        return new NewObject(Identifier.variable(fieldReference, index),
                null, fieldReference.parameterizedType(), Diamond.SHOW_ALL, List.of(),
                notNull, false, null, null);
    }

    public static NewObject initialValueOfExternalVariableField(FieldReference fieldReference,
                                                                String index,
                                                                int minimalNotNull) {
        return new NewObject(Identifier.variable(fieldReference, index), null,
                fieldReference.parameterizedType(), Diamond.SHOW_ALL, List.of(), minimalNotNull,
                false, null, null);
    }

    // null-status derived from variable in evaluation context
    public static NewObject genericMergeResult(String index, VariableInfo variableInfo) {
        int notNull = MultiLevel.bestNotNull(variableInfo.getProperty(VariableProperty.NOT_NULL_EXPRESSION),
                variableInfo.variable().parameterizedType().defaultNotNull());
        return new NewObject(Identifier.variable(variableInfo.variable(), index),
                null, variableInfo.variable().parameterizedType(),
                Diamond.SHOW_ALL, List.of(), notNull,
                variableInfo.variable() instanceof ParameterInfo p && p.index == 0,
                null, null);
    }


    // null-status derived from variable in evaluation context
    public static NewObject genericMergeResult(String index,
                                               Variable variable,
                                               int notNull) {
        return new NewObject(Identifier.variable(variable, index), null, variable.parameterizedType(),
                Diamond.SHOW_ALL, List.of(), notNull,
                false,
                null, null);
    }

    // null-status derived from variable in evaluation context
    public static NewObject genericMergeResult(String index,
                                               VariableInfo variableInfo,
                                               int notNull) {
        return new NewObject(Identifier.variable(variableInfo.variable(), index), null, variableInfo.variable().parameterizedType(),
                Diamond.SHOW_ALL, List.of(), notNull,
                variableInfo.variable() instanceof ParameterInfo p && p.index == 0,
                null, null);
    }

    public static Expression genericArrayAccess(Identifier identifier,
                                                EvaluationContext evaluationContext,
                                                Expression array,
                                                Variable variable) {
        int notNull = evaluationContext.getProperty(array, VariableProperty.NOT_NULL_EXPRESSION, true, false);
        if (notNull == Level.DELAY) {
            return DelayedExpression.forNewObject(variable.parameterizedType(), Level.DELAY,
                    LinkedVariables.sameValue(Stream.concat(Stream.of(variable), array.variables().stream()),
                            LinkedVariables.DELAYED_VALUE));
        }
        int notNullOfElement = MultiLevel.composeOneLevelLess(notNull);
        return new NewObject(identifier, null, variable.parameterizedType(), Diamond.SHOW_ALL, List.of(), notNullOfElement,
                false, null, null);
    }

    /*
    When creating an anonymous instance of a class (new SomeType() { })
     */
    public static NewObject withAnonymousClass(@NotNull ParameterizedType parameterizedType,
                                               @NotNull TypeInfo anonymousClass,
                                               Diamond diamond) {
        return new NewObject(Identifier.generate(), null, parameterizedType, diamond,
                List.of(), MultiLevel.EFFECTIVELY_NOT_NULL, false, anonymousClass, null);
    }

    /*
    Result of actual object creation expressions (new XX, xx::new, ...)
     */
    public static NewObject objectCreation(Identifier identifier,
                                           MethodInfo constructor,
                                           ParameterizedType parameterizedType,
                                           Diamond diamond,
                                           List<Expression> parameterExpressions) {
        return new NewObject(identifier, constructor, parameterizedType, diamond, parameterExpressions, MultiLevel.EFFECTIVELY_NOT_NULL,
                false, null, null);
    }

    /*
   getInstance is used by MethodCall to enrich an instance with state.

   cannot be null, we're applying a method on it.
    */
    public static NewObject forGetInstance(Identifier identifier,
                                           ParameterizedType parameterizedType) {
        return new NewObject(identifier, null, parameterizedType, Diamond.SHOW_ALL, List.of(),
                MultiLevel.EFFECTIVELY_NOT_NULL, false, null, null);
    }

    public NewObject removeConstructor() {
        return new NewObject(identifier, null, parameterizedType, diamond, List.of(),
                Math.max(MultiLevel.EFFECTIVELY_NOT_NULL, minimalNotNull),
                identity,
                anonymousClass, arrayInitializer);
    }

    public NewObject(Identifier identifier,
                     MethodInfo constructor,
                     ParameterizedType parameterizedType,
                     Diamond diamond,
                     List<Expression> parameterExpressions,
                     int minimalNotNull,
                     boolean identity,
                     TypeInfo anonymousClass,
                     ArrayInitializer arrayInitializer) {
        this.identifier = identifier;
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.constructor = constructor; // can be null after modification (constructor lost)
        this.anonymousClass = anonymousClass;
        this.arrayInitializer = arrayInitializer;
        this.minimalNotNull = minimalNotNull;
        assert minimalNotNull != Level.DELAY;
        this.diamond = parameterizedType.parameters.isEmpty() ? Diamond.NO : diamond;
        this.identity = identity;
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
                minimalNotNull == newObject.minimalNotNull;
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, parameterExpressions, anonymousClass, constructor,
                arrayInitializer, minimalNotNull);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new NewObject(identifier,
                constructor,
                translationMap.translateType(parameterizedType),
                diamond,
                parameterExpressions.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                minimalNotNull,
                identity,
                anonymousClass, // not translating this yet!
                arrayInitializer == null ? null : TranslationMapImpl.ensureExpressionType(arrayInitializer, ArrayInitializer.class));
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INSTANCE;
    }


    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        // instance, no constructor parameter expressions
        if (constructor == null) return LinkedVariables.EMPTY;

        return linkedVariablesFromParameters(evaluationContext, constructor.methodInspection.get(),
                parameterExpressions);
    }

    static LinkedVariables linkedVariablesFromParameters(EvaluationContext evaluationContext,
                                                         MethodInspection methodInspection,
                                                         List<Expression> parameterExpressions) {
        // quick shortcut
        if (parameterExpressions.isEmpty()) {
            return LinkedVariables.EMPTY;
        }

        LinkedVariables result = LinkedVariables.EMPTY;
        int i = 0;
        for (Expression value : parameterExpressions) {
            ParameterInfo parameterInfo = methodInspection.getParameters().get(i);
            ParameterAnalysis parameterAnalysis = evaluationContext.getAnalyserContext().getParameterAnalysis(parameterInfo);
            int independentOnParameter = parameterAnalysis.getProperty(VariableProperty.INDEPENDENT);
            LinkedVariables sub = value.linkedVariables(evaluationContext);
            if (independentOnParameter == Level.DELAY) {
                result = result.mergeDelay(sub);
            } else if (independentOnParameter >= MultiLevel.DEPENDENT && independentOnParameter < MultiLevel.INDEPENDENT) {
                result = result.merge(sub, MultiLevel.fromIndependentToLinkedVariableLevel(independentOnParameter));
            }
            i++;
        }
        return result;
    }

    @Override
    public int internalCompareTo(Expression v) {
        return parameterizedType.detailedString()
                .compareTo(((NewObject) v).parameterizedType.detailedString());
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
            case IDENTITY:
                return Level.fromBool(identity);

            case CONTEXT_MODIFIED:
            case CONTEXT_MODIFIED_DELAY:
            case PROPAGATE_MODIFICATION_DELAY:
            case IGNORE_MODIFICATIONS:
                return Level.FALSE;

            case INDEPENDENT:
                return parameterizedType.defaultIndependent(evaluationContext.getAnalyserContext());

            case CONTAINER: { // must be pretty similar to the code in ParameterAnalysis, because every parameter will be of this type
                Boolean transparent = parameterizedType.isTransparent(evaluationContext.getAnalyserContext(),
                        evaluationContext.getCurrentType());
                if (transparent == Boolean.TRUE) return Level.TRUE;
                // if implicit is null, we cannot return FALSE, we'll have to wait!
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                int withoutDelay;
                if (bestType != null) {
                    withoutDelay = evaluationContext.getAnalyserContext()
                            .getTypeAnalysis(bestType).getProperty(VariableProperty.CONTAINER);
                } else {
                    withoutDelay = Level.FALSE;
                }
                return transparent == null && withoutDelay != Level.TRUE ? Level.DELAY : withoutDelay;
            }
            case IMMUTABLE: {
                int immutable = parameterizedType.defaultImmutable(evaluationContext.getAnalyserContext(), false);
                if (constructor != null) {
                    if (immutable == MultiLevel.EVENTUALLY_E1IMMUTABLE)
                        return MultiLevel.EVENTUALLY_E1IMMUTABLE_BEFORE_MARK;
                    if (immutable == MultiLevel.EVENTUALLY_E2IMMUTABLE)
                        return MultiLevel.EVENTUALLY_E2IMMUTABLE_BEFORE_MARK;
                }
                assert immutable != Level.DELAY || evaluationContext.translatedDelay(this.toString(),
                        parameterizedType.bestTypeInfo().fullyQualifiedName + DelayDebugger.D_IMMUTABLE,
                        this + "@" + evaluationContext.statementIndex() + DelayDebugger.D_IMMUTABLE);
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
        // TODO not consistent, but hack after changing 10s of tests, don't want to change back again
        if (identity) {
            outputBuilder.add(new Text("/*@Identity*/"));
        }
        return outputBuilder;
    }

    private String text() {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (Primitives.isPrimitiveExcludingVoid(bestType)) return "";
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
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        List<EvaluationResult> reParams = parameterExpressions.stream().map(v -> v.reEvaluate(evaluationContext, translation)).collect(Collectors.toList());
        List<Expression> reParamValues = reParams.stream().map(EvaluationResult::value).collect(Collectors.toList());
        NewObject newObject = new NewObject(identifier, constructor, parameterizedType,
                diamond, reParamValues, minimalNotNull, identity, anonymousClass, arrayInitializer);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).compose(reParams);
        return builder.setExpression(newObject).build();
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
                evaluationContext, forwardEvaluationInfo, constructor, false, null);

        ParameterizedType pt;
        if (anonymousClass != null) {
            pt = anonymousClass.asParameterizedType(evaluationContext.getAnalyserContext());
        } else {
            pt = parameterizedType;
        }
        NewObject initialInstance = NewObject.objectCreation(identifier, constructor, pt, diamond, res.v);
        Expression instance;
        if (constructor != null) {
            // check state changes of companion methods
            MethodAnalysis constructorAnalysis = evaluationContext.getAnalyserContext().getMethodAnalysis(constructor);
            Expression no = MethodCall.checkCompanionMethodsModifying(res.k, evaluationContext, this,
                    constructor, constructorAnalysis, null, initialInstance, res.v);
            instance = no == null ? DelayedExpression.forNewObject(parameterizedType, MultiLevel.EFFECTIVELY_NOT_NULL,
                    initialInstance.linkedVariables(evaluationContext).changeAllToDelay()) : no;
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
                    .filter(pta -> pta.primaryTypes.contains(anonymousClass))
                    .forEach(res.k::markVariablesFromPrimaryTypeAnalyser);
        }

        int immutable = forwardEvaluationInfo.getProperty(VariableProperty.CONTEXT_IMMUTABLE);
        if (MultiLevel.isAfterThrowWhenNotEventual(immutable)) {
            res.k.raiseError(getIdentifier(), Message.Label.EVENTUAL_AFTER_REQUIRED);
        }
        return res.k.build();
    }

    @Override
    public Identifier getIdentifier() {
        return identifier;
    }
}
