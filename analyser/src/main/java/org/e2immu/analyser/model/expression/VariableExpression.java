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
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

@E2Container
public final class VariableExpression extends BaseExpression implements IsVariableExpression {

    public interface Suffix extends Comparable<Suffix> {

        default OutputBuilder output() {
            return new OutputBuilder();
        }
    }

    public static final Suffix NO_SUFFIX = new Suffix() {
        @Override
        public int hashCode() {
            return 1;
        }

        @Override
        public int compareTo(Suffix o) {
            return o == NO_SUFFIX ? 0 : -1; // I always come first
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

        @Override
        public int compareTo(Suffix o) {
            if (o instanceof VariableField vf) {
                return toString().compareTo(vf.toString());
            }
            return 1;
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

        @Override
        public int compareTo(Suffix o) {
            if (o instanceof VariableInLoop vil) {
                return assignmentId.compareTo(vil.assignmentId);
            }
            return -1;
        }
    }

    private final Variable variable;
    private final Suffix suffix;
    private final Expression scopeValue;

    public VariableExpression(Variable variable) {
        this(variable, NO_SUFFIX, variable instanceof FieldReference fr && !fr.isStatic ? fr.scope : null);
    }

    public VariableExpression(Variable variable, Suffix suffix, Expression scopeValue) {
        super(Identifier.constant(variable.fullyQualifiedName() + suffix));
        this.variable = variable;
        this.suffix = Objects.requireNonNull(suffix);
        if (variable instanceof FieldReference fieldReference && !fieldReference.isStatic) {
            this.scopeValue = Objects.requireNonNull(scopeValue);
        } else {
            this.scopeValue = null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VariableExpression that)) return false;
        if (variable instanceof FieldReference fr && that.variable instanceof FieldReference thatFr) {
            if (fr.isStatic) return fr.equals(thatFr);
            if (thatFr.isStatic) return false;
            return fr.fieldInfo.equals(thatFr.fieldInfo) && suffix.equals(that.suffix) && scopeValue.equals(that.scopeValue);
        }
        return variable.equals(that.variable) && suffix.equals(that.suffix);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_VARIABLE;
    }

    @Override
    public int internalCompareTo(Expression v) {
        InlineConditional ic;
        Expression e;
        if ((ic = v.asInstanceOf(InlineConditional.class)) != null) {
            e = ic.condition;
        } else e = v;
        IsVariableExpression ive;
        if ((ive = e.asInstanceOf(IsVariableExpression.class)) != null) {
            // compare variables
            return variableId().compareTo(ive.variableId());
        }
        throw new UnsupportedOperationException();
    }

    public Suffix getSuffix() {
        return suffix;
    }

    public Expression getScopeValue() {
        return scopeValue;
    }

    public boolean isDependentOnStatementTime() {
        return suffix instanceof VariableField;
    }

    @Override
    public int hashCode() {
        return variable.hashCode() + (scopeValue == null ? 0 : 37 * scopeValue.hashCode()) + 37 * suffix.hashCode();
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        // removes all suffixes!
        Variable translated = translationMap.translateVariable(variable);
        if (translated != variable) {
            throw new UnsupportedOperationException("to implement");
        }
        Expression translated2 = translationMap.translateExpression(this);
        if (translated2 != this) {
            return translated2;
        }
        // helps with bypassing suffixes
        Expression translated3 = translationMap.translateVariableExpressionNullIfNotTranslated(variable);
        if (translated3 != null) {
            return translated3;
        }
        return this;
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isNumeric() {
        TypeInfo typeInfo = variable.parameterizedType().bestTypeInfo();
        return typeInfo != null && typeInfo.isNumeric();
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        if (forwardEvaluationInfo.doNotReevaluateVariableExpressions()) {
            return builder.setExpression(this).build();
        }
        EvaluationResult scopeResult = evaluateScope(context, forwardEvaluationInfo);
        if (scopeResult != null) builder.compose(scopeResult);

        Expression currentValue = builder.currentExpression(variable, scopeResult == null ? null : scopeResult.value(),
                forwardEvaluationInfo);

        builder.setExpression(currentValue);

        // no statement analyser... no need to compute all these properties
        // mind that all evaluation contexts deriving from the one in StatementAnalyser must have
        // non-null current statement!! (see e.g. InlinedMethod)
        if (context.evaluationContext().getCurrentStatement() == null) {
            return builder.build();
        }

        if (variable instanceof This thisVar && !thisVar.typeInfo.equals(context.getCurrentType())) {
            builder.markRead(context.evaluationContext().currentThis());
        }
        if (forwardEvaluationInfo.isNotAssignmentTarget()) {
            builder.markRead(variable);
            VariableExpression ve;
            if ((ve = currentValue.asInstanceOf(VariableExpression.class)) != null) {
                builder.markRead(ve.variable);
                if (ve.variable instanceof This thisVar && !thisVar.typeInfo.equals(context.getCurrentType())) {
                    builder.markRead(context.evaluationContext().currentThis());
                }
            }
        }

        DV notNull = forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL);
        builder.variableOccursInNotNullContext(variable, currentValue, notNull, forwardEvaluationInfo.complainInlineConditional());

        DV modified = forwardEvaluationInfo.getProperty(Property.CONTEXT_MODIFIED);
        builder.markContextModified(variable, modified);
        // do not check for implicit this!! otherwise, any x.y will also affect this.y

        // if super is modified, then this should be modified to
        if (variable instanceof This thisVar && !thisVar.typeInfo.equals(context.getCurrentType())) {
            builder.markContextModified(context.evaluationContext().currentThis(), modified);
        }

        DV contextContainer = forwardEvaluationInfo.getProperty(Property.CONTEXT_CONTAINER);
        builder.variableOccursInContainerContext(variable, contextContainer, forwardEvaluationInfo.complainInlineConditional());

        DV contextImmutable = forwardEvaluationInfo.getProperty(Property.CONTEXT_IMMUTABLE);
        DV nextImmutable = forwardEvaluationInfo.getProperty(Property.NEXT_CONTEXT_IMMUTABLE);
        builder.variableOccursInEventuallyImmutableContext(getIdentifier(), variable, contextImmutable, nextImmutable);


        // having done all this, we do try for a shortcut
        if (scopeResult != null && variable instanceof FieldReference fr) {
            Expression shortCut = tryShortCut(context, scopeResult.value(), fr);
            if (shortCut != null) {
                builder.setExpression(shortCut);
            }
        }
        return builder.build(forwardEvaluationInfo.isNotAssignmentTarget());
    }

    private EvaluationResult evaluateScope(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (variable instanceof FieldReference fr) {
            if (fr.isStatic) {
                // no need to do an assignment
                return fr.scope.evaluate(context, forwardEvaluationInfo.notNullNotAssignment());
            }
            // there is a scope variable
            if (fr.scope instanceof VariableExpression ve) {
                // do not continue modification onto This: we want modifications on this only when there's a direct method call
                ForwardEvaluationInfo forward = fr.scopeIsThis() ? forwardEvaluationInfo.notNullNotAssignment() :
                        forwardEvaluationInfo.copyModificationEnsureNotNull();
                return ve.evaluate(context, forward);
            }
            assert fr.scopeVariable instanceof LocalVariableReference lvr && lvr.variableNature() instanceof VariableNature.ScopeVariable;
            ForwardEvaluationInfo forward = forwardEvaluationInfo.copyModificationEnsureNotNull();
            VariableExpression scopeVE = new VariableExpression(fr.scopeVariable);
            Assignment assignment = new Assignment(context.getPrimitives(), scopeVE, fr.scope);
            return assignment.evaluate(context, forward);
        }
        return null;
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
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
        if (descendIntoFieldReferences && variable instanceof FieldReference fr && !fr.isStatic) {
            return ListUtil.concatImmutable(scopeValue.variables(true), List.of(variable));
        }
        return List.of(variable);
    }

    @Override
    public List<Variable> variablesWithoutCondition() {
        if (variable instanceof FieldReference fr && !fr.scopeIsThis()) {
            return ListUtil.concatImmutable(fr.scope.variablesWithoutCondition(), List.of(variable));
        }
        return List.of(variable);
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        if (variable instanceof FieldReference fr && !fr.isStatic) {
            OutputBuilder outputBuilder = new OutputBuilder();
            if (!fr.isDefaultScope) {
                outputBuilder.add(outputInParenthesis(qualification, precedence(), scopeValue)).add(Symbol.DOT);
            }
            return outputBuilder.add(new Text(fr.fieldInfo.name)).add(suffix.output());
        }
        return new OutputBuilder().add(variable.output(qualification)).add(suffix.output());
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
        if (variable instanceof FieldReference fr && !fr.scopeIsThis()) {
            return List.of(fr.scope);
        }
        return List.of();
    }

    public static Expression tryShortCut(EvaluationResult evaluationContext, Expression scopeValue, FieldReference fr) {
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
        return null;
    }

    private static Expression extractNewObject(EvaluationResult context,
                                               ConstructorCall constructorCall,
                                               FieldInfo fieldInfo) {
        int i = 0;
        List<ParameterAnalysis> parameterAnalyses = context.evaluationContext()
                .getParameterAnalyses(constructorCall.constructor()).toList();
        for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
            Map<FieldInfo, DV> assigned = parameterAnalysis.getAssignedToField();
            if (assigned != null) {
                DV assignedOrLinked = assigned.get(fieldInfo);
                if (assignedOrLinked != null && LinkedVariables.isAssigned(assignedOrLinked)) {
                    return constructorCall.getParameterExpressions().get(i);
                }
            }
            i++;
        }
        return null;
    }

    public Variable variable() {
        return variable;
    }

    // used by internal compare to
    @Override
    public String variableId() {
        StringBuilder sb = new StringBuilder(variable.fullyQualifiedName());
        if (suffix != NO_SUFFIX) sb.append(suffix.toString());
        if (scopeValue != null) sb.append("#").append(scopeValue.output(Qualification.FULLY_QUALIFIED_NAME));
        return sb.toString();
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            variable.visit(predicate);
        }
    }
}
