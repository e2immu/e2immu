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
import java.util.Set;
import java.util.function.Predicate;

import static org.e2immu.analyser.model.expression.ArrayAccess.ARRAY_VARIABLE;
import static org.e2immu.analyser.model.expression.ArrayAccess.INDEX_VARIABLE;

@E2Container
public class VariableExpression extends BaseExpression implements IsVariableExpression {

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
    // only used when the variable is a FieldReference, with !isStatic, OR a DependentVariable
    private final Expression scopeValue;
    // only used when the variable is a DependentVariable
    private final Expression indexValue;

    public VariableExpression(Identifier identifier, Variable variable) {
        this(identifier, variable, NO_SUFFIX, variable instanceof FieldReference fr && !fr.isStatic ? fr.scope :
                        variable instanceof DependentVariable dv ? dv.arrayExpression() : null,
                variable instanceof DependentVariable dv ? dv.indexExpression() : null);
    }

    public VariableExpression(Variable variable) {
        this(Identifier.constant(variable.fullyQualifiedName() + NO_SUFFIX),
                variable, NO_SUFFIX, variable instanceof FieldReference fr && !fr.isStatic ? fr.scope :
                        variable instanceof DependentVariable dv ? dv.arrayExpression() : null,
                variable instanceof DependentVariable dv ? dv.indexExpression() : null);
    }

    public VariableExpression(Variable variable, Suffix suffix, Expression scopeValue, Expression indexValue) {
        this(Identifier.constant(variable.fullyQualifiedName() + suffix), variable, suffix, scopeValue, indexValue);
    }

    public VariableExpression(Identifier identifier, Variable variable, Suffix suffix, Expression scopeValue, Expression indexValue) {
        super(identifier);
        this.variable = variable;
        this.suffix = Objects.requireNonNull(suffix);
        if (variable instanceof FieldReference fieldReference && !fieldReference.isStatic ||
                variable instanceof DependentVariable) {
            this.scopeValue = Objects.requireNonNull(scopeValue);
        } else {
            this.scopeValue = null;
        }
        if (variable instanceof DependentVariable) {
            this.indexValue = indexValue;
        } else {
            this.indexValue = null;
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
        if (variable instanceof DependentVariable && that.variable instanceof DependentVariable) {
            return this.scopeValue.equals(that.scopeValue) && indexValue.equals(that.indexValue);
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

    public Expression getIndexValue() {
        return indexValue;
    }

    public boolean isDependentOnStatementTime() {
        return suffix instanceof VariableField;
    }

    @Override
    public int hashCode() {
        int hc = variable instanceof FieldReference fr ? fr.fieldInfo.hashCode() :
                variable instanceof DependentVariable ? 0 : variable.hashCode();
        return hc + (scopeValue == null ? 0 : 37 * scopeValue.hashCode()) + 37 * suffix.hashCode()
                - 89 * (indexValue == null ? 0 : indexValue.hashCode());
    }

    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        // see explanation in TranslationMapImpl for the order of translation.
        Expression translated1 = translationMap.translateExpression(this);
        if (translated1 != this) {
            return translated1;
        }
        Expression translated2 = translationMap.translateVariableExpressionNullIfNotTranslated(variable);
        if (translated2 != null) {
            return translated2;
        }
        Variable translated3 = translationMap.translateVariable(variable);
        if (translated3 != variable) {
            return new VariableExpression(translated3);
        }
        if (translationMap.recurseIntoScopeVariables()) {
            if (variable instanceof FieldReference fr) {
                Expression translated = fr.scope.translate(inspectionProvider, translationMap);
                if (translated != fr.scope) {
                    FieldReference newFr = new FieldReference(inspectionProvider, fr.fieldInfo, translated, fr.getOwningType());
                    if (translated.isDelayed()) {
                        int statementTime = translated instanceof DelayedVariableExpression dve ? dve.statementTime : 0;
                        return DelayedVariableExpression.forField(newFr, statementTime, translated.causesOfDelay());
                    }
                    return new VariableExpression(fr.scope.getIdentifier(), newFr, suffix, translated, null);
                }
            } else if (variable instanceof DependentVariable dv) {
                Expression translatedScope = dv.arrayExpression().translate(inspectionProvider, translationMap);
                Expression translatedIndex = dv.indexExpression().translate(inspectionProvider, translationMap);
                if (translatedScope != dv.arrayExpression() || translatedIndex != dv.indexExpression()) {
                    Variable arrayVariable = ArrayAccess.makeVariable(translatedScope, translatedScope.getIdentifier(),
                            ARRAY_VARIABLE, dv.getOwningType());
                    assert arrayVariable != null;
                    Variable indexVariable = ArrayAccess.makeVariable(translatedIndex, translatedIndex.getIdentifier(),
                            INDEX_VARIABLE, dv.getOwningType());
                    DependentVariable newDv = new DependentVariable(dv.getIdentifier(), translatedScope,
                            arrayVariable, translatedIndex, indexVariable, dv.parameterizedType(), dv.statementIndex);
                    if (newDv.causesOfDelay().isDelayed()) {
                        return DelayedVariableExpression.forDependentVariable(newDv, newDv.causesOfDelay());
                    }
                    return new VariableExpression(dv.getIdentifier(), newDv, suffix, translatedScope, translatedIndex);
                }
            }
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

    public ForwardEvaluationInfo overrideForward(ForwardEvaluationInfo asParameter) {
        return asParameter;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfoIn) {
        ForwardEvaluationInfo forwardEvaluationInfo = overrideForward(forwardEvaluationInfoIn);
        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        if (forwardEvaluationInfo.isDoNotReevaluateVariableExpressions()) {
            return builder.setExpression(this).build();
        }
        ForwardEvaluationInfo fwd = forwardEvaluationInfo.copy().notNullNotAssignment().build();
        EvaluationResult scopeResult = evaluateScope(context, fwd);
        if (scopeResult != null) builder.compose(scopeResult);
        EvaluationResult indexResult = evaluateIndex(context, fwd);
        if (indexResult != null) builder.compose(indexResult);

        Variable source;
        if (variable instanceof DependentVariable) {
            if (scopeResult.value() instanceof ArrayInitializer initializer && indexResult.value() instanceof Numeric in) {
                // known array, known index (a[] = {1,2,3}, a[2] == 3)
                int intIndex = in.getNumber().intValue();
                if (intIndex < 0 || intIndex >= initializer.multiExpression.expressions().length) {
                    throw new ArrayIndexOutOfBoundsException();
                }
                return builder.setExpression(initializer.multiExpression.expressions()[intIndex]).build();
            }
            assert scopeResult != null;
            assert indexResult != null;
            source = context.evaluationContext().searchInEquivalenceGroupForLatestAssignment((DependentVariable) variable,
                    scopeResult.value(), indexResult.value(), forwardEvaluationInfo);
        } else {
            source = variable;
        } // TODO implement this "source" choice for field reference scope as well

        Expression currentValue = builder.currentExpression(source, scopeResult == null ? null : scopeResult.value(),
                indexResult == null ? null : indexResult.value(), forwardEvaluationInfo);

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
        if (!forwardEvaluationInfo.isAssignmentTarget()) {
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
        // FIXME this is a hack, see Modification_20/tryShortCut -- do we want to keep this?
        if (currentValue instanceof DelayedExpression de) {
            de.shortCutVariables(context.getCurrentType(), scopeValue).forEach((v, expr) -> {
                expr.variables(true).forEach(vv -> {
                    builder.variableOccursInNotNullContext(vv, expr, de.causesOfDelay(), forwardEvaluationInfo);
                });
            });
        }
        builder.variableOccursInNotNullContext(variable, currentValue, notNull, forwardEvaluationInfo);

        DV modified = forwardEvaluationInfo.getProperty(Property.CONTEXT_MODIFIED);
        builder.markContextModified(variable, modified);
        // do not check for implicit this!! otherwise, any x.y will also affect this.y

        // if super is modified, then this should be modified to
        if (variable instanceof This thisVar && !thisVar.typeInfo.equals(context.getCurrentType())) {
            builder.markContextModified(context.evaluationContext().currentThis(), modified);
        }

        DV contextContainer = forwardEvaluationInfo.getProperty(Property.CONTEXT_CONTAINER);
        builder.variableOccursInContainerContext(variable, contextContainer, forwardEvaluationInfo.isComplainInlineConditional());

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
        return builder.build(!forwardEvaluationInfo.isAssignmentTarget());
    }

    private EvaluationResult evaluateScope(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (variable instanceof FieldReference fr) {
            ForwardEvaluationInfo.Builder builder = forwardEvaluationInfo.copy();
            if (fr.isStatic) {
                // no need to do an assignment
                return fr.scope.evaluate(context, builder.notNullNotAssignment().build());
            }
            // there is a scope variable
            if (fr.scope instanceof VariableExpression ve) {
                // do not continue modification onto This: we want modifications on this only when there's a direct method call
                ForwardEvaluationInfo forward = fr.scopeIsThis()
                        ? builder.notNullNotAssignment().build()
                        : builder.ensureModificationSetNotNull().build();
                return ve.evaluate(context, forward);
            }
            if (forwardEvaluationInfo.isEvaluatingFieldExpression()) {
                // the field analyser does not know local variables, so no need for assignments
                return new EvaluationResult.Builder(context).setExpression(fr.scope).build();
            }
            assert fr.scopeVariable instanceof LocalVariableReference lvr && lvr.variableNature() instanceof VariableNature.ScopeVariable;
            ForwardEvaluationInfo forward = builder.ensureModificationSetNotNull().build();
            VariableExpression scopeVE = new VariableExpression(fr.scopeVariable);
            Assignment assignment = new Assignment(context.getPrimitives(), scopeVE, fr.scope);
            return assignment.evaluate(context, forward);
        }
        if (variable instanceof DependentVariable dv) {
            return evaluateForArray(context, forwardEvaluationInfo, dv.arrayExpression(), dv.arrayVariable(), true);
        }
        return null;
    }

    private EvaluationResult evaluateIndex(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        if (variable instanceof DependentVariable dv) {
            // there is an index variable
            return evaluateForArray(context, forwardEvaluationInfo, dv.indexExpression(), dv.indexVariable(),
                    false);
        }
        return null;
    }

    // also used by ArrayAccess
    public static EvaluationResult evaluateForArray(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo,
                                                    Expression expression, Variable variable,
                                                    boolean increaseCnn) {
        if (expression instanceof ConstantExpression<?>) {
            return new EvaluationResult.Builder(context).setExpression(expression).build();
        }
        if (expression instanceof VariableExpression ve) {
            DV cnn = forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL);
            DV higher = increaseCnn ? MultiLevel.composeOneLevelMoreNotNull(cnn) : cnn;
            return ve.evaluate(context, forwardEvaluationInfo.copy().notNullNotAssignment(higher).build());
        }
        assert variable instanceof LocalVariableReference lvr && lvr.variableNature() instanceof VariableNature.ScopeVariable;
        ForwardEvaluationInfo forward = forwardEvaluationInfo.copy().ensureModificationSetNotNull().build();
        VariableExpression scopeVE = new VariableExpression(variable);
        Assignment assignment = new Assignment(context.getPrimitives(), scopeVE, expression);
        return assignment.evaluate(context, forward);
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        return LinkedVariables.of(variable, LinkedVariables.STATICALLY_ASSIGNED_DV);
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
        OutputBuilder outputBuilder = new OutputBuilder();
        if (variable instanceof FieldReference fr && !fr.isStatic) {
            if (!fr.isDefaultScope) {
                outputBuilder.add(outputInParenthesis(qualification, precedence(), scopeValue)).add(Symbol.DOT);
            }
            outputBuilder.add(new Text(fr.fieldInfo.name));
        } else if (variable instanceof DependentVariable) {
            outputBuilder.add(outputInParenthesis(qualification, precedence(), scopeValue))
                    .add(Symbol.LEFT_BRACKET).add(indexValue.output(qualification)).add(Symbol.RIGHT_BRACKET);
        } else {
            outputBuilder.add(variable.output(qualification));
        }
        return outputBuilder.add(suffix.output());
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

    public static Expression tryShortCut(EvaluationResult context, Expression scopeValue, FieldReference fr) {
        ConstructorCall constructorCall;
        if ((constructorCall = scopeValue.asInstanceOf(ConstructorCall.class)) != null && constructorCall.constructor() != null) {
            return extractNewObject(context, constructorCall, fr.fieldInfo);
        }
        if (scopeValue instanceof VariableExpression scopeVe && scopeVe.variable instanceof FieldReference scopeFr) {
            FieldAnalysis fieldAnalysis = context.getAnalyserContext().getFieldAnalysis(scopeFr.fieldInfo);
            Expression efv = fieldAnalysis.getValue();
            ConstructorCall cc2;
            if (efv != null && (cc2 = efv.asInstanceOf(ConstructorCall.class)) != null && cc2.constructor() != null) {
                return extractNewObject(context, cc2, fr.fieldInfo);
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
    public void visit(Predicate<Element> predicate) {
        if (predicate.test(this)) {
            variable.visit(predicate);
        }
    }

    @Override
    public Set<Variable> loopSourceVariables() {
        return Set.of(variable);
    }

    @Override
    public Set<Variable> directAssignmentVariables() {
        return Set.of(variable);
    }

    @Override
    public DV hardCodedPropertyOrNull(Property property) {
        if (variable instanceof This && property == Property.NOT_NULL_EXPRESSION) {
            return MultiLevel.EFFECTIVELY_NOT_NULL_DV;
        }
        return null;
    }
}
