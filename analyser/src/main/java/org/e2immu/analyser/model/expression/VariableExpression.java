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
import org.e2immu.analyser.analysis.FieldAnalysis;
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
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@E2Container
public final class VariableExpression extends BaseExpression implements Expression, IsVariableExpression {

    public interface Suffix {

        default OutputBuilder output() {
            return new OutputBuilder();
        }
    }

    public static final Suffix NO_SUFFIX = new Suffix() {
        @Override
        public int hashCode() {
            return 1;
        }
    };

    public record VariableField(int statementTime, String assignmentId) implements Suffix {
        public VariableField {
            assert statementTime >= 0; // otherwise, no suffix!
        }

        @Override
        public String toString() {
            if (assignmentId == null) return "$" + statementTime;
            return "$" + assignmentId + "$" + statementTime;
        }

        @Override
        public OutputBuilder output() {
            OutputBuilder outputBuilder = new OutputBuilder();
            if (assignmentId != null) outputBuilder.add(new Text("$" + assignmentId));
            outputBuilder.add(new Text("$" + statementTime));
            return outputBuilder;
        }
    }

    public record VariableInLoop(String assignmentId) implements Suffix {
        @Override
        public String toString() {
            return "$" + assignmentId;
        }

        @Override
        public OutputBuilder output() {
            return new OutputBuilder().add(new Text("$" + assignmentId));
        }
    }

    private final Variable variable;
    private final Suffix suffix;

    public VariableExpression(Variable variable) {
        this(variable, NO_SUFFIX);
    }

    public VariableExpression(Variable variable, Suffix suffix) {
        super(Identifier.CONSTANT);
        this.variable = variable;
        this.suffix = suffix;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariableExpression that)) return false;
        if (!variable.equals(that.variable)) return false;
        return Objects.equals(suffix, that.suffix);
    }

    public Suffix getSuffix() {
        return suffix;
    }

    public boolean isDependentOnStatementTime() {
        return suffix instanceof VariableField;
    }

    @Override
    public int hashCode() {
        return variable.hashCode() + 37 * suffix.hashCode();
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        Variable translated = translationMap.translateVariable(variable);
        if (translated != variable) {
            return new VariableExpression(translated, suffix);
        }
        Expression translated2 = translationMap.directExpression(this);
        if (translated2 != null) {
            return translated2;
        }
        if (variable instanceof FieldReference fieldReference && fieldReference.scope != null) {
            Expression translatedScope = fieldReference.scope.translate(translationMap);
            if (!translatedScope.equals(fieldReference.scope)) {
                ParameterizedType translatedType = translationMap.translateType(fieldReference.parameterizedType());
                return new VariableExpression(new FieldReference(fieldReference.fieldInfo, translatedScope,
                        translatedType, fieldReference.isStatic, fieldReference.isDefaultScope), suffix);
            }
        }
        return this;
    }

    @Override
    public DV getProperty(EvaluationContext evaluationContext, Property property, boolean duringEvaluation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_VARIABLE;
    }

    @Override
    public int internalCompareTo(Expression v) {
        VariableExpression variableValue;
        if (v instanceof InlineConditional inlineConditional)
            variableValue = (VariableExpression) inlineConditional.condition;
        else if (v instanceof VariableExpression ve) variableValue = ve;
        else throw new UnsupportedOperationException();
        return id().compareTo(variableValue.id());
    }

    private String id() {
        if (suffix != NO_SUFFIX) return variable.fullyQualifiedName() + suffix;
        return variable.fullyQualifiedName();
    }

    @Override
    public boolean isNumeric() {
        TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
        return typeInfo != null && typeInfo.isNumeric();
    }

    /*
    the purpose of having this extra "markRead" here (as compared to the default implementation in Expression),
    is to ensure that fields exist when they are encountered -- reEvaluate is called from the single return value of
    method; if this one returns a field, that field has to be made available to the next iteration; see Enum_3 statement 0 in
    posInList

    Full evaluation causes a lot of trouble with improper delays because we have no decent ForwardEvaluationInfo
     */
    @Override
    public EvaluationResult reEvaluate(EvaluationContext evaluationContext, Map<Expression, Expression> translation) {
        Expression inMap = translation.get(this);
        if (inMap != null) {
            VariableExpression ve;
            if ((ve = inMap.asInstanceOf(VariableExpression.class)) != null) {
                return ve.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
            }
            DelayedVariableExpression dve;
            EvaluationResult.Builder builder = new EvaluationResult.Builder();
            if ((dve = inMap.asInstanceOf(DelayedVariableExpression.class)) != null) {
                // causes problems with local copies (Loops_19)
                //   return dve.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT);
                builder.markRead(dve.variable());
                return builder.setExpression(dve).build();
            }
            return builder.setExpression(inMap).build();
        }
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext).markRead(variable);
        if (variable instanceof FieldReference fieldReference && fieldReference.scope instanceof VariableExpression ve) {
            // the variable itself is not in the map, but we may have to substitute
            // (see EventuallyImmutableUtil_5, s1.bool with substitution s1 -> t.s1
            // IMPROVE how should we go recursive here? we should call reEvaluate, but may bump into
            // unknown fields (t is known, but t.s1 is not), which causes infinite delays.
            Expression scopeInMap = translation.get(ve);
            VariableExpression newScope;
            if (scopeInMap != null && (newScope = scopeInMap.asInstanceOf(VariableExpression.class)) != null) {
                Variable newFieldRef = new FieldReference(evaluationContext.getAnalyserContext(), fieldReference.fieldInfo, newScope);
                return builder.setExpression(new VariableExpression(newFieldRef)).build();
            }
        }
        return builder.setExpression(new VariableExpression(variable)).build();
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
        EvaluationResult scopeResult;
        if (variable instanceof FieldReference fr && fr.scope != null) {
            // do not continue modification onto This: we want modifications on this only when there's a direct method call
            ForwardEvaluationInfo forward = fr.scopeIsThis() ? forwardEvaluationInfo.notNullNotAssignment() :
                    forwardEvaluationInfo.copyModificationEnsureNotNull();
            scopeResult = fr.scope.evaluate(evaluationContext, forward);
            builder.compose(scopeResult);
        } else {
            scopeResult = null;
        }

        Expression currentValue = builder.currentExpression(variable, forwardEvaluationInfo);
        Expression adjustedScope = adjustScope(evaluationContext, scopeResult, currentValue);

        builder.setExpression(adjustedScope);

        // no statement analyser... no need to compute all these properties
        // mind that all evaluation contexts deriving from the one in StatementAnalyser must have
        // non-null current statement!! (see e.g. InlinedMethod)
        if (evaluationContext.getCurrentStatement() == null) {
            return builder.build();
        }

        if (variable instanceof This thisVar && !thisVar.typeInfo.equals(evaluationContext.getCurrentType())) {
            builder.markRead(evaluationContext.currentThis());
        }
        if (forwardEvaluationInfo.isNotAssignmentTarget()) {
            builder.markRead(variable);
            VariableExpression ve;
            if ((ve = adjustedScope.asInstanceOf(VariableExpression.class)) != null) {
                builder.markRead(ve.variable);
                if (ve.variable instanceof This thisVar && !thisVar.typeInfo.equals(evaluationContext.getCurrentType())) {
                    builder.markRead(evaluationContext.currentThis());
                }
            }
        }

        DV notNull = forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL);
        builder.variableOccursInNotNullContext(variable, adjustedScope, notNull);

        DV modified = forwardEvaluationInfo.getProperty(Property.CONTEXT_MODIFIED);
        builder.markContextModified(variable, modified);
        // do not check for implicit this!! otherwise, any x.y will also affect this.y

        // if super is modified, then this should be modified to
        if (variable instanceof This thisVar && !thisVar.typeInfo.equals(evaluationContext.getCurrentType())) {
            builder.markContextModified(evaluationContext.currentThis(), modified);
        }

        DV contextContainer = forwardEvaluationInfo.getProperty(Property.CONTEXT_CONTAINER);
        builder.variableOccursInContainerContext(variable, contextContainer);

        DV contextImmutable = forwardEvaluationInfo.getProperty(Property.CONTEXT_IMMUTABLE);
        DV nextImmutable = forwardEvaluationInfo.getProperty(Property.NEXT_CONTEXT_IMMUTABLE);
        builder.variableOccursInEventuallyImmutableContext(getIdentifier(), variable, contextImmutable, nextImmutable);


        // having done all this, we do try for a shortcut
        if (scopeResult != null) {
            Expression shortCut = tryShortCut(evaluationContext, scopeResult.value(), adjustedScope);
            if (shortCut != null) {
                builder.setExpression(shortCut);
            }
        }
        return builder.build(forwardEvaluationInfo.isNotAssignmentTarget());
    }

    static Expression adjustScope(EvaluationContext evaluationContext,
                                  EvaluationResult scopeResult,
                                  Expression currentValue) {
        if (scopeResult != null) {
            CausesOfDelay scopeResultIsDelayed = evaluationContext.isDelayed(scopeResult.getExpression());
            InspectionProvider inspectionProvider = evaluationContext.getAnalyserContext();
            if (currentValue instanceof VariableExpression ve
                    && ve.variable() instanceof FieldReference fr
                    && !fr.scope.equals(scopeResult.value())) {
                if (!scopeResultIsDelayed.isDelayed()) {
                    FieldReference newFieldRef = new FieldReference(inspectionProvider, fr.fieldInfo, scopeResult.getExpression());
                    return new VariableExpression(newFieldRef, ve.suffix);
                }
                return DelayedVariableExpression.forField(fr, scopeResultIsDelayed);
            }
            if (currentValue instanceof DelayedVariableExpression ve
                    && ve.variable() instanceof FieldReference fr && !fr.scope.equals(scopeResult.value())) {
                if (!scopeResultIsDelayed.isDelayed()) {
                    return DelayedVariableExpression.forField(new FieldReference(inspectionProvider, fr.fieldInfo, scopeResult.getExpression()),
                            ve.causesOfDelay);
                }
                return DelayedVariableExpression.forField(fr, ve.causesOfDelay);
            }
        }
        return currentValue;
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        return new LinkedVariables(Map.of(variable, LinkedVariables.STATICALLY_ASSIGNED_DV));
    }

    @Override
    public ParameterizedType returnType() {
        return variable.parameterizedType();
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        if (descendIntoFieldReferences && variable instanceof FieldReference fr && fr.scope != null && !fr.scopeIsThis()) {
            return ListUtil.concatImmutable(fr.scope.variables(true), List.of(variable));
        }
        return List.of(variable);
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder outputBuilder = new OutputBuilder().add(variable.output(qualification)).add(suffix.output());
        return outputBuilder;
    }

    @Override
    public String toString() {
        return minimalOutput();
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return variable.typesReferenced(false);
    }

    @Override
    public boolean hasState() {
        throw new UnsupportedOperationException("Use evaluationContext.hasState()");
    }

    @Override
    public List<? extends Element> subElements() {
        if (variable instanceof FieldReference fr && fr.scope != null && !fr.scopeIsThis()) {
            return List.of(fr.scope);
        }
        return List.of();
    }

    private static Expression tryShortCut(EvaluationContext evaluationContext, Expression scopeValue, Expression variableValue) {
        if (variableValue instanceof VariableExpression ve && ve.variable instanceof FieldReference fr) {
            ConstructorCall constructorCall;
            if ((constructorCall = scopeValue.asInstanceOf(ConstructorCall.class)) != null && constructorCall.constructor() != null) {
                return extractNewObject(evaluationContext, constructorCall, fr.fieldInfo);
            }
            if (scopeValue instanceof VariableExpression scopeVe && scopeVe.variable instanceof FieldReference scopeFr) {
                FieldAnalysis fieldAnalysis = evaluationContext.getAnalyserContext().getFieldAnalysis(scopeFr.fieldInfo);
                Expression efv = fieldAnalysis.getValue();
                ConstructorCall cc2;
                if (efv != null && (cc2 = efv.asInstanceOf(ConstructorCall.class)) != null && cc2.constructor() != null) {
                    return extractNewObject(evaluationContext, cc2, fr.fieldInfo);
                }
            }
        }
        return null;
    }

    private static Expression extractNewObject(EvaluationContext evaluationContext,
                                               ConstructorCall constructorCall,
                                               FieldInfo fieldInfo) {
        int i = 0;
        List<ParameterAnalysis> parameterAnalyses = evaluationContext
                .getParameterAnalyses(constructorCall.constructor()).collect(Collectors.toList());
        for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
            Map<FieldInfo, DV> assigned = parameterAnalysis.getAssignedToField();
            DV assignedOrLinked = assigned.get(fieldInfo);
            if (LinkedVariables.isAssigned(assignedOrLinked)) {
                return constructorCall.getParameterExpressions().get(i);
            }
            i++;
        }
        return null;
    }

    public Variable variable() {
        return variable;
    }
}
