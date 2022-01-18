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
import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private final MethodInfo methodInfo;
    private final Expression expression;
    private final Set<VariableExpression> variablesOfExpression;
    private final boolean containsVariableFields;

    public InlinedMethod(Identifier identifier,
                         MethodInfo methodInfo,
                         Expression expression,
                         Set<VariableExpression> variablesOfExpression,
                         boolean containsVariableFields) {
        super(identifier);
        this.methodInfo = Objects.requireNonNull(methodInfo);
        this.expression = Objects.requireNonNull(expression);
        this.variablesOfExpression = variablesOfExpression;
        this.containsVariableFields = containsVariableFields;
    }

    public static Expression of(Identifier identifier,
                                MethodInfo methodInfo,
                                Expression expression,
                                AnalyserContext analyserContext) {
        Predicate<FieldReference> predicate = containsVariableFields(analyserContext);
        return of(identifier, methodInfo, expression, predicate);
    }

    private static Expression of(Identifier identifier,
                                 MethodInfo methodInfo,
                                 Expression expression,
                                 Predicate<FieldReference> isVariableField) {
        Set<VariableExpression> variableExpressions = new HashSet<>();
        AtomicBoolean containsVariableFields = new AtomicBoolean();
        AtomicReference<CausesOfDelay> causes = new AtomicReference<>(CausesOfDelay.EMPTY);
        expression.visit(e -> {
            if (e instanceof VariableExpression ve) {
                variableExpressions.add(ve);
                if (ve.variable() instanceof FieldReference fr) {
                    boolean contains = isVariableField.test(fr);
                    if (contains) containsVariableFields.set(true);
                }
                // do not descend into scopes which are VEs as well
                return false;
            }
            if (e instanceof DelayedVariableExpression dve) {
                causes.set(causes.get().merge(dve.causesOfDelay));
                return false;
            }
            return true;
        });
        if (causes.get().isDone()) {
            return new InlinedMethod(identifier, methodInfo, expression, Set.copyOf(variableExpressions),
                    containsVariableFields.get());
        }
        return DelayedExpression.forInlinedMethod(expression.returnType(), causes.get());
    }

    private static Predicate<FieldReference> containsVariableFields(AnalyserContext analyserContext) {
        return fr -> {
            FieldAnalysis fieldAnalysis = analyserContext.getFieldAnalysis(fr.fieldInfo);
            DV effectivelyFinal = fieldAnalysis.getProperty(Property.FINAL);
            return effectivelyFinal.valueIsFalse();
        };
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return of(identifier, methodInfo, expression.translate(translationMap), fr -> containsVariableFields);
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
        return new OutputBuilder().add(new Text("", "/* inline " + methodInfo.name + " */"))
                .add(expression.output(qualification));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TERNARY;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        throw new UnsupportedOperationException();
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
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        return evaluationContext.getAnalyserContext().getMethodAnalysis(methodInfo).getProperty(property);
    }

    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        Set<Variable> targetVariables = translation.values().stream()
                .flatMap(e -> e.variables(true).stream()).collect(Collectors.toUnmodifiableSet());
        EvaluationContext closure = new EvaluationContextImpl(evaluationContext, targetVariables);
        EvaluationResult result = expression.reEvaluate(closure, translation);
        if (expression instanceof InlinedMethod im) {
            Expression newIm = of(identifier, im.methodInfo(), result.getExpression(), evaluationContext.getAnalyserContext());
            return new EvaluationResult.Builder().compose(result).setExpression(newIm).build();
        }
        return result;
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
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            expression.visit(predicate);
        }
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        return variablesOfExpression.stream().map(VariableExpression::variable).toList();
    }

    public boolean canBeApplied(EvaluationContext evaluationContext) {
        return !containsVariableFields ||
                evaluationContext.getCurrentType().primaryType().equals(methodInfo.typeInfo.primaryType());
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

    /*
    We're assuming that the parameters of the method occur in the value, so it's simpler to iterate
     */
    public Map<Expression, Expression> translationMap(EvaluationContext evaluationContext,
                                                      List<Expression> parameters,
                                                      Expression scope,
                                                      TypeInfo typeOfTranslation,
                                                      Identifier identifierOfMethodCall) {
        Map<Expression, Expression> builder = new HashMap<>();
        InspectionProvider inspectionProvider = evaluationContext.getAnalyserContext();

        for (VariableExpression variableExpression : variablesOfExpression) {
            Variable variable = variableExpression.variable();
            Expression replacement = null;
            boolean replace = true;
            FieldReference fieldReference = null;
            if (variable instanceof ParameterInfo parameterInfo) {
                if (parameterInfo.getMethod() == methodInfo) {
                    replacement = parameterReplacement(parameters, inspectionProvider, parameterInfo);
                } else {
                    replace = false;
                }
            } else if (variable instanceof This) {
                VariableExpression ve;
                if ((ve = scope.asInstanceOf(VariableExpression.class)) != null) {
                    builder.put(new VariableExpression(variable), ve);
                }
            } else if (variable instanceof FieldReference fr) {
                fieldReference = fr;
            }

            if (fieldReference != null) {
                boolean staticField = fieldReference.fieldInfo.isStatic(inspectionProvider);
                boolean scopeIsThis = fieldReference.scopeIsThis();
                if (!staticField && !scopeIsThis) {
                    replace = false;
                    // the variables of the scope are dealt with separately
                } else if (visibleIn(inspectionProvider, fieldReference.fieldInfo, typeOfTranslation)) {
                    // maybe the final field is linked to a parameter, and we have a value for that parameter?

                    FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext()
                            .getFieldAnalysis(fieldReference.fieldInfo);
                    DV effectivelyFinal = fieldAnalysis.getProperty(Property.FINAL);
                    if (effectivelyFinal.valueIsTrue()) {
                        if (staticField) {
                            replace = false;
                        } else {
                            replacement = replacementForConstructorCall(evaluationContext, scope, fieldReference);
                        }
                    } else if (effectivelyFinal.valueIsFalse()) {
                        // variable field: replace the VE with Suffix by one without (i$0 --> i)
                        replacement = replacementForVariableField(inspectionProvider, fieldReference, scope);
                    }
                    VariableExpression ve;
                    if (replacement == null && (ve = scope.asInstanceOf(VariableExpression.class)) != null) {
                        replacement = replacementForScopeField(evaluationContext, inspectionProvider, fieldReference,
                                staticField, ve);
                    }
                }
            }
            if (replace) {
                Expression toMap = ensureReplacement(evaluationContext, identifierOfMethodCall, variable, replacement);
                builder.put(variableExpression, toMap);
            }
        }
        return Map.copyOf(builder);
    }

    private Expression replacementForVariableField(InspectionProvider inspectionProvider,
                                                   FieldReference fieldReference,
                                                   Expression scope) {
        return new VariableExpression(new FieldReference(inspectionProvider, fieldReference.fieldInfo, scope));
    }

    private Expression replacementForScopeField(EvaluationContext evaluationContext,
                                                InspectionProvider inspectionProvider,
                                                FieldReference fieldReference,
                                                boolean staticField,
                                                VariableExpression ve) {
        FieldReference scopeField = new FieldReference(inspectionProvider, fieldReference.fieldInfo,
                staticField ? null : ve);
        CausesOfDelay causesOfDelay = evaluationContext.variableIsDelayed(scopeField);
        if (causesOfDelay.isDelayed()) {
            return DelayedVariableExpression.forField(scopeField, causesOfDelay);
        }
        return new VariableExpression(scopeField);
    }

    private Expression replacementForConstructorCall(EvaluationContext evaluationContext,
                                                     Expression scope,
                                                     FieldReference fieldReference) {
        ConstructorCall constructorCall = bestConstructorCall(evaluationContext, scope);
        if (constructorCall != null && constructorCall.constructor() != null) {
            // only now we can start to take a look at the parameters
            int index = indexOfParameterLinkedToFinalField(evaluationContext, constructorCall.constructor(),
                    fieldReference.fieldInfo);
            if (index >= 0) {
                return constructorCall.getParameterExpressions().get(index);
            }
        }
        return null;
    }

    private Expression parameterReplacement(List<Expression> parameters,
                                            InspectionProvider inspectionProvider,
                                            ParameterInfo parameterInfo) {
        Expression replacement;
        if (parameterInfo.parameterInspection.get().isVarArgs()) {
            replacement = new ArrayInitializer(inspectionProvider, parameters.subList(parameterInfo.index,
                    parameters.size()), parameterInfo.parameterizedType);
        } else {
            replacement = parameters.get(parameterInfo.index);
        }
        return replacement;
    }

    private ConstructorCall bestConstructorCall(EvaluationContext evaluationContext, Expression scope) {
        ConstructorCall constructorCall = scope.asInstanceOf(ConstructorCall.class);
        VariableExpression ve;
        if (constructorCall == null && (ve = scope.asInstanceOf(VariableExpression.class)) != null) {
            Expression value = evaluationContext.currentValue(ve.variable());
            if (value != null) {
                return value.asInstanceOf(ConstructorCall.class);
            } // else, see Loops_19
        }
        return constructorCall;
    }

    private Expression ensureReplacement(EvaluationContext evaluationContext,
                                         Identifier identifierOfMethodCall,
                                         Variable variable,
                                         Expression replacement) {
        if (replacement != null) return replacement;

        AnalyserContext analyserContext = evaluationContext.getAnalyserContext();
        ParameterizedType parameterizedType = variable.parameterizedType();

        Properties valueProperties = analyserContext.defaultValueProperties(parameterizedType);
        CausesOfDelay merged = valueProperties.delays();
        if (merged.isDelayed()) {
            return DelayedExpression.forMethod(methodInfo, variable.parameterizedType(),
                    evaluationContext.linkedVariables(variable).changeAllToDelay(merged), merged);
        }
        return Instance.forGetInstance(Identifier.joined(List.of(identifierOfMethodCall,
                VariableIdentifier.variable(variable))), variable.parameterizedType(), valueProperties);
    }

    private int indexOfParameterLinkedToFinalField(EvaluationContext evaluationContext,
                                                   MethodInfo constructor,
                                                   FieldInfo fieldInfo) {
        int i = 0;
        List<ParameterAnalysis> parameterAnalyses = evaluationContext
                .getParameterAnalyses(constructor).collect(Collectors.toList());
        for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
            if (!parameterAnalysis.assignedToFieldIsFrozen()) {
                return -2; // delays
            }
            Map<FieldInfo, DV> assigned = parameterAnalysis.getAssignedToField();
            DV assignedOrLinked = assigned.get(fieldInfo);
            if (LinkedVariables.isAssigned(assignedOrLinked)) {
                return i;
            }
            i++;
        }
        return -1; // nothing
    }

    private boolean visibleIn(InspectionProvider inspectionProvider, FieldInfo fieldInfo, TypeInfo here) {
        FieldInspection fieldInspection = inspectionProvider.getFieldInspection(fieldInfo);
        return switch (fieldInspection.getAccess()) {
            case PRIVATE -> here.primaryType().equals(fieldInfo.primaryType());
            case PACKAGE -> here.primaryType().packageNameOrEnclosingType.getLeft()
                    .equals(fieldInfo.owner.primaryType().packageNameOrEnclosingType.getLeft());
            case PROTECTED -> here.primaryType().equals(fieldInfo.primaryType()) ||
                    here.hasAsParentClass(inspectionProvider, fieldInfo.owner);
            default -> true;
        };
    }

    private class EvaluationContextImpl extends AbstractEvaluationContextImpl {
        private final EvaluationContext evaluationContext;
        private final Set<Variable> acceptedVariables;

        protected EvaluationContextImpl(EvaluationContext evaluationContext, Set<Variable> targetVariables) {
            super(evaluationContext.getIteration(),
                    ConditionManager.initialConditionManager(evaluationContext.getPrimitives()), null);
            this.evaluationContext = evaluationContext;
            this.acceptedVariables = Stream.concat(variables(true).stream(),
                    targetVariables.stream()).collect(Collectors.toUnmodifiableSet());
        }

        protected EvaluationContextImpl(EvaluationContextImpl parent, ConditionManager conditionManager) {
            super(parent.iteration, conditionManager, null);
            this.evaluationContext = parent.evaluationContext;
            this.acceptedVariables = parent.acceptedVariables;
        }

        private void ensureVariableIsKnown(Variable variable) {
            assert variable instanceof This || acceptedVariables.contains(variable) :
                    "there should be no other variables in this expression: " +
                            variable + " is not in accepted variables\n" + acceptedVariables +
                            " of expression\n" + expression + "\nvariablesOfExpression is " + variablesOfExpression;
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
        public Location getLocation() {
            return evaluationContext.getLocation();
        }

        @Override
        public Location getLocation(Identifier identifier) {
            return evaluationContext.getLocation(identifier);
        }

        @Override
        public Primitives getPrimitives() {
            return evaluationContext.getPrimitives();
        }

        @Override
        public EvaluationContext child(Expression condition) {
            return child(condition, false);
        }

        @Override
        public EvaluationContext dropConditionManager() {
            ConditionManager cm = ConditionManager.initialConditionManager(getPrimitives());
            return new EvaluationContextImpl(this, cm);
        }

        @Override
        public EvaluationContext child(Expression condition, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
            return new EvaluationContextImpl(this,
                    conditionManager.newAtStartOfNewBlockDoNotChangePrecondition(getPrimitives(),
                            condition, condition.causesOfDelay()));
        }

        @Override
        public EvaluationContext childState(Expression state) {
            return new EvaluationContextImpl(this, conditionManager.addState(state, state.causesOfDelay()));
        }

        @Override
        public Expression currentValue(Variable variable, ForwardEvaluationInfo forwardEvaluationInfo) {
            ensureVariableIsKnown(variable);
            return new VariableExpression(variable);
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
            return evaluationContext.getProperty(value, property, duringEvaluation, ignoreStateInConditionManager);
        }

        @Override
        public DV getProperty(Variable variable, Property property) {
            ensureVariableIsKnown(variable);
            return property.falseDv; // FIXME
        }

        @Override
        public DV getPropertyFromPreviousOrInitial(Variable variable, Property property) {
            ensureVariableIsKnown(variable);
            return property.falseDv; // FIXME
        }

        @Override
        public boolean notNullAccordingToConditionManager(Variable variable) {
            return notNullAccordingToConditionManager(variable, evaluationContext::findOrThrow);
        }

        @Override
        public LinkedVariables linkedVariables(Variable variable) {
            ensureVariableIsKnown(variable);
            return LinkedVariables.EMPTY;
        }

        @Override
        public Properties getValueProperties(Expression value) {
            return evaluationContext.getValueProperties(value);
        }

        @Override
        public Properties getValueProperties(ParameterizedType parameterizedType, Expression value, boolean ignoreConditionInConditionManager) {
            return evaluationContext.getValueProperties(parameterizedType, value, ignoreConditionInConditionManager);
        }

        // FIXME should this be delayed? we never want to end up with an InlinedMethod object
        @Override
        public Instance currentValue(Variable variable) {
            ensureVariableIsKnown(variable);
            return Instance.forInlinedMethod(identifier, variable.parameterizedType());
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
        public Expression acceptAndTranslatePrecondition(Expression rest) {
            return evaluationContext.acceptAndTranslatePrecondition(rest);
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
        public CausesOfDelay isDelayed(Expression expression) {
            return CausesOfDelay.EMPTY; // nothing can be delayed here
        }

        @Override
        public This currentThis() {
            return evaluationContext.currentThis();
        }

        @Override
        public boolean cannotBeModified(Expression value) {
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
