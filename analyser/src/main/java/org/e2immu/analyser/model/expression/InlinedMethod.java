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

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.nonanalyserimpl.AbstractEvaluationContextImpl;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.impl.TranslationMapImpl;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
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
    private final Set<Variable> myParameters;
    private final Expression defaultReplacement;

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
        myParameters = Set.copyOf(methodInfo.methodInspection.get().getParameters());
        // used as a marker constant
        defaultReplacement = UnknownExpression.forSpecial();
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
                boolean add = ve.variable() instanceof ParameterInfo pi && pi.owner == methodInfo ||
                        ve.variable() instanceof This ||
                        ve.variable() instanceof LocalVariableReference ||
                        ve.variable() instanceof FieldReference fr && (fr.fieldInfo.isStatic() || fr.scopeIsThis(methodInfo.typeInfo));
                if (add) {
                    variableExpressions.add(ve);
                }
                if (ve.variable() instanceof FieldReference fr) {
                    boolean contains = isVariableField.test(fr);
                    if (contains) containsVariableFields.set(true);
                }
                // descend into scope, expressions of dependent variable!
                return !add || ve.variable() instanceof FieldReference;
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
        return DelayedExpression.forInlinedMethod(identifier, expression.returnType(), causes.get());
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
        boolean allParametersCovered = variablesOfExpression.stream().map(VariableExpression::variable)
                .filter(variable -> variable instanceof ParameterInfo).allMatch(context.evaluationContext()::isPresent);
        boolean haveParameters = variablesOfExpression.stream().anyMatch(ve -> ve.variable() instanceof ParameterInfo);
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
        return of(identifier, methodInfo, translated, fr -> containsVariableFields);
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
        return expression.variables(descendIntoFieldReferences);
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

    public Set<VariableExpression> getVariablesOfExpression() {
        return variablesOfExpression;
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
            Expression replacement = replace(variableExpression, parameters, scope, typeOfTranslation, evaluationContext);
            if (replacement != null) {
                Variable variable = variableExpression.variable();
                Expression toMap = ensureReplacement(evaluationContext, identifierOfMethodCall, variable, replacement);
                builder.put(variableExpression, toMap);
            } // possibly a field need not replacing
        }
        return builder.build();
    }

    private Expression replace(VariableExpression variableExpression,
                               List<Expression> parameters,
                               Expression scope,
                               TypeInfo typeOfTranslation,
                               EvaluationResult evaluationResult) {
        Variable variable = variableExpression.variable();
        InspectionProvider inspectionProvider = evaluationResult.getAnalyserContext();
        if (variable instanceof ParameterInfo parameterInfo) {
            assert parameterInfo.getMethod() == methodInfo : "Parameter " + parameterInfo + " should not have been in variablesOfExpression";
            return parameterReplacement(parameters, inspectionProvider, parameterInfo);
        }
        if (variable instanceof This) {
            if (!methodInfo.methodInspection.get().isStatic()) {
                return scope;
            }
            return defaultReplacement;
        }
        if (variable instanceof FieldReference fieldReference &&
                visibleIn(inspectionProvider, fieldReference.fieldInfo, typeOfTranslation)) {
            // maybe the final field is linked to a parameter, and we have a value for that parameter?

            FieldAnalysis fieldAnalysis = evaluationResult.getAnalyserContext()
                    .getFieldAnalysis(fieldReference.fieldInfo);
            DV effectivelyFinal = fieldAnalysis.getProperty(Property.FINAL);
            if (effectivelyFinal.valueIsTrue()) {
                return replacementForConstructorCall(evaluationResult, scope, fieldReference);// keep the final field
            }
            if (effectivelyFinal.valueIsFalse()) {
                // variable field: replace the VE with Suffix by one without (i$0 --> i)
                return replacementForVariableField(inspectionProvider, fieldReference, scope);
            }
        }

        // e.g., local variable reference, see InlinedMethod_3
        return defaultReplacement;
    }

    private Expression replacementForVariableField(InspectionProvider inspectionProvider,
                                                   FieldReference fieldReference,
                                                   Expression scope) {
        return new VariableExpression(new FieldReference(inspectionProvider, fieldReference.fieldInfo, scope));
    }

    private Expression replacementForConstructorCall(EvaluationResult evaluationContext,
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
        if (parameterInfo.parameterInspection.get().isVarArgs()) {
            return new ArrayInitializer(inspectionProvider, parameters.subList(parameterInfo.index,
                    parameters.size()), parameterInfo.parameterizedType);
        }
        return parameters.get(parameterInfo.index);
    }

    private ConstructorCall bestConstructorCall(EvaluationResult evaluationContext, Expression scope) {
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

    private Expression ensureReplacement(EvaluationResult evaluationContext,
                                         Identifier identifierOfMethodCall,
                                         Variable variable,
                                         Expression replacement) {
        if (replacement != defaultReplacement) return replacement;

        AnalyserContext analyserContext = evaluationContext.getAnalyserContext();
        ParameterizedType parameterizedType = variable.parameterizedType();

        Properties valueProperties = analyserContext.defaultValueProperties(parameterizedType);
        CausesOfDelay merged = valueProperties.delays();
        if (merged.isDelayed()) {
            LinkedVariables lv = evaluationContext.evaluationContext().linkedVariables(variable);
            LinkedVariables changed = lv == null ? LinkedVariables.EMPTY : lv.changeAllToDelay(merged);
            return DelayedExpression.forMethod(identifierOfMethodCall, methodInfo, variable.parameterizedType(),
                    changed, merged);
        }
        return Instance.forGetInstance(Identifier.joined("inline", List.of(identifierOfMethodCall,
                VariableIdentifier.variable(variable))), variable.parameterizedType(), valueProperties);
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

        protected EvaluationContextImpl(EvaluationContext evaluationContext) {
            super(evaluationContext.getDepth() + 1, evaluationContext.getIteration(),
                    evaluationContext.getConditionManager(), null);
            this.evaluationContext = evaluationContext;
        }

        protected EvaluationContextImpl(EvaluationContextImpl parent, ConditionManager conditionManager) {
            super(parent.getDepth() + 1, parent.iteration, conditionManager, null);
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
                    conditionManager.newAtStartOfNewBlockDoNotChangePrecondition(getPrimitives(), condition));
        }

        @Override
        public EvaluationContext childState(Expression state) {
            return new EvaluationContextImpl(this, conditionManager.addState(state));
        }

        @Override
        public Expression currentValue(Variable variable, ForwardEvaluationInfo forwardEvaluationInfo) {
            if (variable instanceof ParameterInfo pi && pi.owner == methodInfo) {
                return new VariableExpression(variable);
            }
            Expression expression = evaluationContext.currentValue(variable, forwardEvaluationInfo);
            return expression;
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
        public LinkedVariables linkedVariables(Variable variable) {
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

        @Override
        public Instance currentValue(Variable variable) {
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
