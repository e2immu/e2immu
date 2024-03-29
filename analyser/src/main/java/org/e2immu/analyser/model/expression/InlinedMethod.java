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
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.expression.util.ExtractVariablesToBeTranslated;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/*
 can only be created as the single result value of a non-modifying method

 will be substituted in MethodCall

 Big question: do properties come from the expression, or from the method??
 In the case of Supplier.get() (which is modifying by default), the expression may for example be a parameterized string (t + "abc").
 The string expression is Level 2 immutable, hence not-modified.

 Properties that rely on the return value, should come from the Value. Properties to do with modification, should come from the method.
 */
public class InlinedMethod extends BaseExpression implements Expression {
    private static final Logger LOGGER = LoggerFactory.getLogger(InlinedMethod.class);

    private final MethodInfo methodInfo;
    private final Expression expression;
    private final Set<VariableExpression> variablesOfExpression;
    private final boolean containsVariableFields;
    private final Set<Variable> myParameters;

    public InlinedMethod(Identifier identifier,
                         MethodInfo methodInfo,
                         Expression expression,
                         Set<VariableExpression> variablesOfExpression,
                         boolean containsVariableFields) {
        super(identifier, expression.getComplexity());
        this.methodInfo = Objects.requireNonNull(methodInfo);
        this.expression = Objects.requireNonNull(expression);
        assert !expression.isDelayed() : "Trying to create an inlined method with delays";
        this.variablesOfExpression = variablesOfExpression;
        this.containsVariableFields = containsVariableFields;
        myParameters = Set.copyOf(methodInfo.methodInspection.get().getParameters());
    }

    public Set<Variable> getMyParameters() {
        return myParameters;
    }

    public static Expression of(Identifier identifier,
                                MethodInfo methodInfo,
                                Expression expression,
                                AnalyserContext analyserContext) {
        Predicate<FieldReference> predicate = containsVariableFields(analyserContext);
        return of(identifier, methodInfo, expression, predicate, analyserContext);
    }

    private static Expression of(Identifier identifier,
                                 MethodInfo methodInfo,
                                 Expression expression,
                                 Predicate<FieldReference> isVariableField,
                                 InspectionProvider inspectionProvider) {
        ExtractVariablesToBeTranslated ev = new ExtractVariablesToBeTranslated(isVariableField, inspectionProvider, false, false);
        expression.visit(ev);
        if (ev.getCauses().isDone()) {
            try {
                Set<VariableExpression> variableExpressions = ev.getExpressions()
                        .stream().map(e -> (VariableExpression) e).collect(Collectors.toUnmodifiableSet());
                return new InlinedMethod(identifier, methodInfo, expression, variableExpressions,
                        ev.isContainsVariableFields());
            } catch (RuntimeException rte) {
                LOGGER.error("Exception: ", rte);
                throw rte;
            }
        }
        return DelayedExpression.forInlinedMethod(identifier, expression.returnType(), expression, ev.getCauses());
    }

    private static Predicate<FieldReference> containsVariableFields(AnalyserContext analyserContext) {
        return fr -> {
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fr.fieldInfo);
            DV effectivelyFinal = fieldAnalysis.getProperty(Property.FINAL);
            return effectivelyFinal.valueIsFalse();
        };
    }

    @Override
    public boolean isNumeric() {
        return expression.isNumeric();
    }

    @Override
    public ParameterizedType returnType() {
        return expression.returnType();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        String msg = "/*inline " + methodInfo.name + "*/";
        return new OutputBuilder().add(new Text(msg)).add(expression.output(qualification));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TERNARY;
    }


    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        boolean allParametersCovered = variablesOfExpression.stream()
                .map(VariableExpression::variable)
                .filter(variable -> variable instanceof ParameterInfo pi && pi.owner == methodInfo)
                .allMatch(context.evaluationContext()::isPresent);
        boolean haveParameters = variablesOfExpression.stream()
                .anyMatch(ve -> ve.variable() instanceof ParameterInfo pi && pi.owner == methodInfo);
        if (!allParametersCovered && haveParameters) {
            // do not evaluate further
            return new EvaluationResult.Builder(context).setExpression(this).build();
        }

        EvaluationContext closure = new EvaluationContextImpl(context.evaluationContext());
        EvaluationResult closureContext = context.copy(closure);
        return expression.evaluate(closureContext, forwardEvaluationInfo);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INLINE_METHOD;
    }

    @Override
    public int internalCompareTo(Expression v) {
        InlinedMethod mv = (InlinedMethod) v;
        return methodInfo.distinguishingName().compareTo(mv.methodInfo.distinguishingName());
    }

    /*
    These values only matter when the InlinedMethod cannot get expanded. In this case, it is simply
    a method call, and the result of the method should be used.
     */
    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        if (property == Property.NOT_NULL_EXPRESSION) {
            return MethodReference.notNull(context, methodInfo);
        }
        return context.getAnalyserContext().getMethodAnalysis(methodInfo).getProperty(property);
    }


    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression translated = expression.translate(inspectionProvider, translationMap);
        if (translated == expression) return this;
        if (translated.isDelayed()) {
            return DelayedExpression.forInlinedMethod(identifier, expression.returnType(),
                    expression, translated.causesOfDelay());
        }
        return of(identifier, methodInfo, translated, fr -> containsVariableFields, inspectionProvider);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InlinedMethod that = (InlinedMethod) o;
        return methodInfo.equals(that.methodInfo) && expression.equals(that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodInfo, expression);
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return expression.variables(descendIntoFieldReferences);
    }

    @Override
    public TypeInfo typeInfoOfReturnType() {
        return methodInfo.typeInfo;
    }

    public boolean canBeApplied(EvaluationResult context) {
        return !containsVariableFields || context.getCurrentType().primaryType().equals(methodInfo.typeInfo.primaryType());
    }

    public MethodInfo methodInfo() {
        return methodInfo;
    }

    public Expression expression() {
        return expression;
    }

    public boolean containsVariableFields() {
        return containsVariableFields;
    }

    public String variablesOfExpressionSorted() {
        return variablesOfExpression.stream().map(Object::toString).sorted().collect(Collectors.joining(", "));
    }

    /*
         We're assuming that the parameters of the method occur in the value, so it's simpler to iterate

         x*y -> variableExpressions = x param 0, y param 1; parameters = new values

         variablesOfExpression have been properly filtered already!
         */
    public TranslationMap translationMap(EvaluationResult evaluationContext,
                                         List<Expression> parameters,
                                         Expression scope,
                                         TypeInfo typeOfTranslation,
                                         Identifier identifierOfMethodCall) {
        TranslationMapImpl.Builder builder = new TranslationMapImpl.Builder();

        for (VariableExpression variableExpression : variablesOfExpression) {
            Expression replacement = replace(variableExpression, parameters, scope, typeOfTranslation, evaluationContext,
                    identifierOfMethodCall);
            if (replacement != null) {
                builder.put(variableExpression, replacement);
            } // possibly a field need not replacing
        }
        return builder.build();
    }

    private Expression replace(VariableExpression variableExpression,
                               List<Expression> parameters,
                               Expression scope,
                               TypeInfo typeOfTranslation,
                               EvaluationResult evaluationResult,
                               Identifier identifierOfMethodCall) {
        Variable variable = variableExpression.variable();
        InspectionProvider inspectionProvider = evaluationResult.getAnalyserContext();
        if (variable instanceof ParameterInfo parameterInfo) {
            if (parameterInfo.getMethod() == methodInfo) {
                Expression parameterReplacement = parameterReplacement(parameters, inspectionProvider, parameterInfo);
                if(parameterReplacement.isInstanceOf(InlinedMethod.class)) {
                    // see e.g., Lookahead.lookAhead, blocking "writer" from being expanded
                    return expandedVariable(evaluationResult, parameterInfo.identifier, DV.TRUE_DV, variable);
                }
                return parameterReplacement;
            }
            // we can get here because of the recursive call when expanding ConstructorCalls
            // see Modification_11
            MethodAnalyser currentMethodAnalyser = evaluationResult.evaluationContext().getCurrentMethod();
            if (currentMethodAnalyser != null && parameterInfo.getMethod() == currentMethodAnalyser.getMethodInfo()) {
                return variableExpression;
            }
            return expandedVariable(evaluationResult, identifierOfMethodCall, null, variable);
        }
        if (variable instanceof This && !methodInfo.methodInspection.get().isStatic()) {
            return scope;
        }
        if (variable instanceof FieldReference fieldReference) {
            // maybe the final field is linked to a parameter, and we have a value for that parameter?

            FieldAnalysis fieldAnalysis = evaluationResult.getAnalyserContext()
                    .getFieldAnalysis(fieldReference.fieldInfo);
            DV effectivelyFinal = fieldAnalysis.getProperty(Property.FINAL);
            Variable modifiedVariable = replaceScope(parameters, scope, typeOfTranslation, evaluationResult,
                    identifierOfMethodCall, variable, inspectionProvider, fieldReference);

            /*
            Lambda_4: simply being present is not good enough to ensure consistency wrt. linked variables
            if (evaluationResult.evaluationContext() != null && evaluationResult.evaluationContext().isPresent(modifiedVariable)) {
                return new VariableExpression(modifiedVariable);
            }*/

            if (effectivelyFinal.valueIsTrue()) {
                ConstructorCall constructorCall = bestConstructorCall(evaluationResult, scope);
                if (constructorCall != null && constructorCall.constructor() != null) {
                    // only now we can start to take a look at the parameters
                    int index = indexOfParameterLinkedToFinalField(evaluationResult, constructorCall.constructor(),
                            fieldReference.fieldInfo);
                    if (index >= 0) {
                        Expression ccValue = constructorCall.getParameterExpressions().get(index);
                        // see Enum_4 as a nice example
                        if (ccValue.isConstant()) return ccValue;
                        if (ccValue instanceof VariableExpression ve) {
                            return replace(ve, parameters, scope, typeOfTranslation, evaluationResult, identifierOfMethodCall);
                        }
                        // Loops_19 shows that we have to expand (d.time)
                    }
                }

                // we use the identifier of the field itself here: every time this field is expanded, it gets the same identifier
                return expandedVariable(evaluationResult, fieldReference.fieldInfo.getIdentifier(), effectivelyFinal,
                        modifiedVariable);
            }
            return expandedVariable(evaluationResult, identifierOfMethodCall, effectivelyFinal, modifiedVariable);
        }

        // e.g., local variable reference, see InlinedMethod_3
        return expandedVariable(evaluationResult, identifierOfMethodCall, null, variable);
    }

    private Variable replaceScope(List<Expression> parameters,
                                  Expression scope,
                                  TypeInfo typeOfTranslation,
                                  EvaluationResult evaluationResult,
                                  Identifier identifierOfMethodCall,
                                  Variable variable,
                                  InspectionProvider inspectionProvider
            , FieldReference fieldReference) {
        Variable modifiedVariable;
        if (fieldReference.scope instanceof VariableExpression ve) {
            Expression replacedScope = replace(ve, parameters, scope, typeOfTranslation, evaluationResult,
                    identifierOfMethodCall);
            modifiedVariable = new FieldReference(inspectionProvider, fieldReference.fieldInfo, replacedScope, fieldReference.getOwningType());
        } else {
            modifiedVariable = variable;
        }
        return modifiedVariable;
    }

    private Expression parameterReplacement(List<Expression> parameters,
                                            InspectionProvider inspectionProvider,
                                            ParameterInfo parameterInfo) {
        if (parameterInfo.parameterInspection.get().isVarArgs()) {
            return new ArrayInitializer(inspectionProvider, parameters.subList(parameterInfo.index,
                    parameters.size()), parameterInfo.parameterizedType);
        }
        return parameters.get(parameterInfo.index);
    }

    private ConstructorCall bestConstructorCall(EvaluationResult context, Expression scope) {
        ConstructorCall constructorCall = scope.asInstanceOf(ConstructorCall.class);
        if (constructorCall != null) return constructorCall;
        if (scope instanceof PropertyWrapper pw && pw.state() instanceof ConstructorCall cc) {
            return cc;
        }
        VariableExpression ve;
        if ((ve = scope.asInstanceOf(VariableExpression.class)) != null) {
            Expression value = context.currentValue(ve.variable());
            if (value != null) {
                return bestConstructorCall(context, value);
            } // else, see Loops_19
        }
        return null;
    }

    private Expression expandedVariable(EvaluationResult context,
                                        Identifier identifierOfMethodCall,
                                        DV effectivelyFinal,
                                        Variable variable) {

        AnalyserContext analyserContext = context.getAnalyserContext();
        ParameterizedType parameterizedType = variable.parameterizedType();

        Properties valueProperties;
        if (variable instanceof FieldReference fr) {
            FieldAnalysis fieldAnalysis = context.getAnalyserContext().getFieldAnalysis(fr.fieldInfo);
             /*
             parameterizedType = HasSize[], field type = type parameter T
             taking all the value properties from the field will not be good.
             See e.g. E2ImmutableComposition_0.EncapsulatedExposedArrayOfHasSize
             */
            if (fr.fieldInfo.type.equals(parameterizedType)) {
                valueProperties = fieldAnalysis.getValueProperties();
            } else {
                valueProperties = Properties.of(Map.of(
                        Property.NOT_NULL_EXPRESSION, fieldAnalysis.getProperty(Property.EXTERNAL_NOT_NULL),
                        Property.IGNORE_MODIFICATIONS, fieldAnalysis.getProperty(Property.EXTERNAL_IGNORE_MODIFICATIONS),
                        Property.IDENTITY, DV.FALSE_DV,
                        Property.IMMUTABLE, analyserContext.defaultImmutable(parameterizedType, false, context.getCurrentType()),
                        Property.INDEPENDENT, analyserContext.defaultIndependent(parameterizedType),
                        Property.CONTAINER, analyserContext.defaultContainer(parameterizedType)
                ));
            }
        } else if (context.evaluationContext().isMyself(parameterizedType)) {
            valueProperties = context.evaluationContext().valuePropertiesOfFormalType(parameterizedType);
        } else {
            valueProperties = analyserContext.defaultValueProperties(parameterizedType, context.getCurrentType());
        }
        CausesOfDelay merged = valueProperties.delays()
                .merge(variable.causesOfDelay())
                .merge(effectivelyFinal == null ? CausesOfDelay.EMPTY : effectivelyFinal.causesOfDelay());
        if (merged.isDelayed()) {
            return DelayedExpression.forMethod(identifierOfMethodCall, methodInfo, variable.parameterizedType(),
                    EmptyExpression.EMPTY_EXPRESSION, // given that we'll return an expanded variable
                    merged, Map.of());
        }
        Identifier inline;
        // non-modifying: make sure it remains the same one
        boolean addStatementTime = variable instanceof FieldReference
                && effectivelyFinal != null && effectivelyFinal.valueIsFalse();
        if (addStatementTime) {
            inline = Identifier.joined("inline",
                    List.of(Identifier.forStatementTime(context.evaluationContext().getInitialStatementTime()),
                            VariableIdentifier.variable(variable)));
        } else {
            // non-modifying method when used for inlining actual method values
            inline = VariableIdentifier.variable(variable);
        }
        return new ExpandedVariable(inline, variable, valueProperties);
    }

    private int indexOfParameterLinkedToFinalField(EvaluationResult context,
                                                   MethodInfo constructor,
                                                   FieldInfo fieldInfo) {
        int i = 0;
        List<ParameterAnalysis> parameterAnalyses = context.evaluationContext().getParameterAnalyses(constructor).toList();
        for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
            if (!parameterAnalysis.assignedToFieldIsFrozen()) {
                return -2; // delays
            }
            Map<FieldInfo, DV> assigned = parameterAnalysis.getAssignedToField();
            DV assignedOrLinked = assigned.get(fieldInfo);
            if (assignedOrLinked != null && LinkedVariables.isAssigned(assignedOrLinked)) {
                return i;
            }
            i++;
        }
        return -1; // nothing
    }

    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {
        private final EvaluationContext evaluationContext;

        protected EvaluationContextImpl(EvaluationContext evaluationContext) {
            super(evaluationContext.getDepth() + 1, evaluationContext.getIteration(),
                    evaluationContext.allowBreakDelay(),
                    evaluationContext.getConditionManager(), null);
            this.evaluationContext = evaluationContext;
        }

        protected EvaluationContextImpl(EvaluationContextImpl parent, ConditionManager conditionManager) {
            super(parent.getDepth() + 1, parent.iteration, parent.allowBreakDelay(), conditionManager, null);
            this.evaluationContext = parent.evaluationContext;
        }

        @Override
        public TypeInfo getCurrentType() {
            return evaluationContext.getCurrentType();
        }

        @Override
        public MethodAnalyser getCurrentMethod() {
            return evaluationContext.getCurrentMethod();
        }

        // important for this to be not null (e.g. PropagateModification_8, it 2 statement 0 of forEach)
        @Override
        public StatementAnalyser getCurrentStatement() {
            return evaluationContext.getCurrentStatement();
        }

        @Override
        public Location getLocation(Stage level) {
            return evaluationContext.getLocation(level);
        }

        @Override
        public Location getEvaluationLocation(Identifier identifier) {
            return evaluationContext.getEvaluationLocation(identifier);
        }

        @Override
        public Primitives getPrimitives() {
            return evaluationContext.getPrimitives();
        }

        @Override
        public EvaluationContext child(Expression condition, Set<Variable> conditionVariables) {
            return child(condition, conditionVariables, false);
        }

        @Override
        public EvaluationContext dropConditionManager() {
            ConditionManager cm = ConditionManager.initialConditionManager(getPrimitives());
            return new EvaluationContextImpl(this, cm);
        }

        @Override
        public EvaluationContext child(Expression condition, Set<Variable> conditionVariables, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
            return new EvaluationContextImpl(this,
                    conditionManager.newAtStartOfNewBlockDoNotChangePrecondition(getPrimitives(), condition, conditionVariables));
        }

        @Override
        public EvaluationContext childState(Expression state, Set<Variable> stateVariables) {
            return new EvaluationContextImpl(this, conditionManager.addState(state, stateVariables));
        }

        @Override
        public Expression currentValue(Variable variable,
                                       Expression scopeValue,
                                       Expression indexValue,
                                       ForwardEvaluationInfo forwardEvaluationInfo) {
            if (variable instanceof ParameterInfo pi && pi.owner == methodInfo) {
                return new VariableExpression(variable);
            }
            return evaluationContext.currentValue(variable, scopeValue, indexValue, forwardEvaluationInfo);
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return evaluationContext.getAnalyserContext();
        }

        @Override
        public Stream<ParameterAnalysis> getParameterAnalyses(MethodInfo methodInfo) {
            return evaluationContext.getParameterAnalyses(methodInfo);
        }

        @Override
        public DV getProperty(Expression value, Property property, boolean duringEvaluation, boolean ignoreStateInConditionManager) {
            if (value instanceof VariableExpression ve) {
                return getVariableProperty(ve.variable(), property, duringEvaluation);
            }
            return evaluationContext.getProperty(value, property, duringEvaluation, ignoreStateInConditionManager);
        }

        private DV getVariableProperty(Variable variable, Property property, boolean duringEvaluation) {
            if (duringEvaluation) {
                return getPropertyFromPreviousOrInitial(variable, property);
            }
            return getProperty(variable, property);
        }

        @Override
        public DV getProperty(Variable variable, Property property) {
            if (myParameters.contains(variable)) {
                LOGGER.debug("Enquiring after {} in method {}", variable.simpleName(), methodInfo.fullyQualifiedName);
                return property.falseDv;
            }
            if (evaluationContext.isPresent(variable)) {
                return evaluationContext.getProperty(variable, property);
            }
            // other.isSet() expands to other.t, with other a parameter, t a known field, but other.t not yet known
            return getPropertyFromPreviousOrInitial(variable, property);
        }

        @Override
        public DV getPropertyFromPreviousOrInitial(Variable variable, Property property) {
            if (variable instanceof ParameterInfo pi && myParameters.contains(pi)) {
                LOGGER.debug("Enquiring after {} in method {}", pi.simpleName(), methodInfo.fullyQualifiedName);
                return property.falseDv;
            }
            return evaluationContext.getPropertyFromPreviousOrInitial(variable, property);
        }

        @Override
        public Properties getValueProperties(Expression value) {
            return evaluationContext.getValueProperties(value);
        }

        @Override
        public Properties getValueProperties(ParameterizedType parameterizedType, Expression value, boolean ignoreConditionInConditionManager) {
            return evaluationContext.getValueProperties(parameterizedType, value, ignoreConditionInConditionManager);
        }

        @Override
        public Expression currentValue(Variable variable) {
            return UnknownExpression.forUnknownReturnValue(identifier, variable.parameterizedType());
        }

        @Override
        public boolean disableEvaluationOfMethodCallsUsingCompanionMethods() {
            return evaluationContext.disableEvaluationOfMethodCallsUsingCompanionMethods();
        }

        @Override
        public int getInitialStatementTime() {
            return evaluationContext.getInitialStatementTime();
        }

        @Override
        public int getFinalStatementTime() {
            return evaluationContext.getFinalStatementTime();
        }

        @Override
        public boolean allowedToIncrementStatementTime() {
            return evaluationContext.allowedToIncrementStatementTime();
        }

        @Override
        public Expression replaceLocalVariables(Expression expression) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Expression acceptAndTranslatePrecondition(Identifier identifier, Expression rest) {
            return evaluationContext.acceptAndTranslatePrecondition(identifier, rest);
        }

        @Override
        public boolean isPresent(Variable variable) {
            return variablesOfExpression.stream().map(VariableExpression::variable).anyMatch(v -> v.equals(variable));
        }

        @Override
        public List<PrimaryTypeAnalyser> getLocalPrimaryTypeAnalysers() {
            return evaluationContext.getLocalPrimaryTypeAnalysers();
        }

        @Override
        public Stream<Map.Entry<String, VariableInfoContainer>> localVariableStream() {
            return evaluationContext.localVariableStream();
        }

        @Override
        public MethodAnalysis findMethodAnalysisOfLambda(MethodInfo methodInfo) {
            return evaluationContext.findMethodAnalysisOfLambda(methodInfo);
        }

        @Override
        public CausesOfDelay variableIsDelayed(Variable variable) {
            return CausesOfDelay.EMPTY; // nothing can be delayed here
        }

        @Override
        public This currentThis() {
            return evaluationContext.currentThis();
        }

        @Override
        public DV cannotBeModified(Expression value) {
            return evaluationContext.cannotBeModified(value);
        }

        @Override
        public MethodInfo concreteMethod(Variable variable, MethodInfo methodInfo) {
            return evaluationContext.concreteMethod(variable, methodInfo);
        }

        @Override
        public String statementIndex() {
            return evaluationContext.statementIndex();
        }

        @Override
        public boolean firstAssignmentOfFieldInConstructor(Variable variable) {
            return evaluationContext.firstAssignmentOfFieldInConstructor(variable);
        }

        @Override
        public boolean hasBeenAssigned(Variable variable) {
            return evaluationContext.hasBeenAssigned(variable);
        }

        @Override
        public boolean hasState(Expression expression) {
            return false;
        }

        @Override
        public Expression state(Expression expression) {
            return evaluationContext.state(expression);
        }
    }
}
