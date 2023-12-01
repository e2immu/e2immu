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
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analyser.impl.context.EvaluationResultImpl;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.analyser.model.variable.impl.FieldReferenceImpl;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.graph.analyser.PackedInt;
import org.e2immu.analyser.util.PackedIntMap;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.support.Either;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

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

    public record ModifiedVariable(String assignmentId) implements Suffix {
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
            if (o instanceof ModifiedVariable mv) {
                return assignmentId.compareTo(mv.assignmentId);
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
        this(identifier, variable, NO_SUFFIX, variable instanceof FieldReference fr && !fr.isStatic() ? fr.scope() :
                        variable instanceof DependentVariable dv ? dv.arrayExpression() : null,
                variable instanceof DependentVariable dv ? dv.indexExpression() : null);
    }

    public VariableExpression(Identifier identifier, Variable variable, Suffix suffix, Expression scopeValue, Expression indexValue) {
        super(identifier, variable.getComplexity());
        this.variable = variable;
        this.suffix = Objects.requireNonNull(suffix);
        if (variable instanceof FieldReference fieldReference && !fieldReference.isStatic() ||
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
            if (fr.isStatic()) return fr.equals(thatFr);
            if (thatFr.isStatic()) return false;
            return fr.fieldInfo().equals(thatFr.fieldInfo()) && suffix.equals(that.suffix) && scopeValue.equals(that.scopeValue);
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
    public int internalCompareTo(Expression v) throws ExpressionComparator.InternalError {
        IsVariableExpression ive;
        if ((ive = v.asInstanceOf(IsVariableExpression.class)) != null) {
            // compare variables
            return variableId().compareTo(ive.variableId());
        }
        throw new ExpressionComparator.InternalError();
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
        int hc = variable instanceof FieldReference fr ? fr.fieldInfo().hashCode() :
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
        Variable translated3 = translationMap.translateVariable(inspectionProvider, variable);
        if (translated3 != variable) {
            return new VariableExpression(identifier, translated3);
        }
        if (translationMap.recurseIntoScopeVariables()) {
            if (variable instanceof FieldReference fr) {
                Expression translated = fr.scope().translate(inspectionProvider, translationMap);
                if (translated != fr.scope()) {
                    FieldReference newFr = new FieldReferenceImpl(inspectionProvider, fr.fieldInfo(), translated, fr.getOwningType());
                    if (translated.isDelayed()) {
                        int statementTime = translated instanceof DelayedVariableExpression dve ? dve.statementTime : 0;
                        return DelayedVariableExpression.forField(newFr, statementTime, translated.causesOfDelay());
                    }
                    return new VariableExpression(fr.scope().getIdentifier(), newFr, suffix, translated, null);
                }
            } else if (variable instanceof DependentVariable dv) {
                Expression translatedScope = dv.arrayExpression().translate(inspectionProvider, translationMap);
                Expression translatedIndex = dv.indexExpression().translate(inspectionProvider, translationMap);
                if (translatedScope != dv.arrayExpression() || translatedIndex != dv.indexExpression()) {
                    Variable arrayVariable = DependentVariable.makeVariable(translatedScope, translatedScope.getIdentifier(),
                            DependentVariable.ARRAY_VARIABLE, dv.getOwningType());
                    assert arrayVariable != null;
                    Variable indexVariable = DependentVariable.makeVariable(translatedIndex, translatedIndex.getIdentifier(),
                            DependentVariable.INDEX_VARIABLE, dv.getOwningType());
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
        TypeInfo typeInfo = variable.parameterizedType().typeInfo;
        return typeInfo != null && typeInfo.isNumeric();
    }

    public ForwardEvaluationInfo overrideForward(ForwardEvaluationInfo asParameter) {
        return asParameter;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfoIn) {
        EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context);
        if (forwardEvaluationInfoIn.isOnlySort()) {
            return builder.setExpression(this).build();
        }
        ForwardEvaluationInfo forwardEvaluationInfo = overrideForward(forwardEvaluationInfoIn);
        if (forwardEvaluationInfo.isDoNotReevaluateVariableExpressions()) {
            return builder.setExpression(this).build();
        }
        ForwardEvaluationInfo fwd = forwardEvaluationInfo.copy().notNullNotAssignment().build();
        EvaluationResult scopeResult = evaluateScope(context, fwd);
        if (scopeResult != null) builder.compose(scopeResult);
        EvaluationResult indexResult = evaluateIndex(context, fwd);
        if (indexResult != null) builder.compose(indexResult);

        Variable source;
        if (variable instanceof DependentVariable dv) {
            assert scopeResult != null;
            assert indexResult != null;
            Expression computedScope = scopeResult.value();
            DV link = computedScope.isDelayed() ? computedScope.causesOfDelay() : LinkedVariables.LINK_IS_HC_OF;
            builder.link(dv, dv.arrayVariable(), link);

            if (computedScope instanceof ArrayInitializer initializer && indexResult.value() instanceof Numeric in) {
                // known array, known index (a[] = {1,2,3}, a[2] == 3)
                int intIndex = in.getNumber().intValue();
                if (intIndex < 0 || intIndex >= initializer.multiExpression.expressions().length) {
                    throw new ArrayIndexOutOfBoundsException();
                }
                return builder.setExpression(initializer.multiExpression.expressions()[intIndex]).build();
            }


            if (dv.arrayVariable() instanceof LocalVariableReference lvr
                    && lvr.variableNature() instanceof VariableNature.ScopeVariable
                    && !(computedScope.isInstanceOf(IsVariableExpression.class))
                    && !forwardEvaluationInfo.isAssignmentTarget()) {
                // methodCall()[index] as a value rather than the target of an assignment
                Properties properties = context.evaluationContext().defaultValueProperties(dv.parameterizedType);
                CausesOfDelay delays = properties.delays();
                LinkedVariables lv = scopeResult.linkedVariables(lvr);
                assert lv != null : "We have recently evaluated the arrayVariable";
                Expression replacement;
                if (delays.isDelayed() || lv.isDelayed()) {
                    replacement = DelayedExpression.forArrayAccessValue(dv.getIdentifier(), dv.parameterizedType,
                            new VariableExpression(dv.getIdentifier(), dv), delays.merge(lv.causesOfDelay()));
                } else {
                    Expression instance = Instance.forArrayAccess(dv.getIdentifier(),
                            context.evaluationContext().statementIndex(), dv.parameterizedType, properties);
                    if (lv.isEmpty()) {
                        replacement = instance;
                    } else {
                        replacement = PropertyWrapper.propertyWrapper(instance, lv.minimum(LinkedVariables.LINK_IS_HC_OF));
                    }
                }
                return builder.setExpression(replacement).build();
            }

            source = context.evaluationContext().searchInEquivalenceGroupForLatestAssignment((DependentVariable) variable,
                    computedScope, indexResult.value(), forwardEvaluationInfo);
        } else {
            source = variable;
        } // TODO implement this "source" choice for field reference scope as well

        Expression currentValue = builder.currentExpression(source, scopeResult == null ? null : scopeResult.value(),
                indexResult == null ? null : indexResult.value(), identifier, forwardEvaluationInfo);
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

        if (currentValue instanceof DelayedExpression de) {
            // See WGSimplified_0: make sure we have delays on context properties linked to the result
            CausesOfDelay marker = DelayFactory.createDelay(new VariableCause(variable, context.evaluationContext().getLocation(Stage.EVALUATION), CauseOfDelay.Cause.DELAYED_EXPRESSION));
            CausesOfDelay causesOfDelay = de.causesOfDelay().merge(marker);
            boolean haveMarker = de.causesOfDelay().containsCauseOfDelay(CauseOfDelay.Cause.DELAYED_EXPRESSION);
            if (!haveMarker) {
                for (Variable variable : de.variables()) {
                    if (!this.variable.equals(variable) && context.evaluationContext().isPresent(variable)) {
                        for (Property ctx : Property.CONTEXTS) {
                            // we must delay, even if there's a value already
                            builder.setProperty(variable, ctx, causesOfDelay);
                        }
                    }
                }
            }

            // IMPORTANT: this is a hack, see Modification_20/tryShortCut -- do we want to keep this?
            de.shortCutVariables(context.getCurrentType(), scopeValue).forEach((v, expr) ->
                    expr.variableStream().forEach(vv ->
                            builder.variableOccursInNotNullContext(vv, expr, de.causesOfDelay(), forwardEvaluationInfo)));
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
            if (fr.isStatic()) {
                // no need to do an assignment
                return fr.scope().evaluate(context, builder.notNullNotAssignment().build());
            }
            // there is a scope variable
            if (fr.scope() instanceof VariableExpression ve) {
                // do not continue modification onto This: we want modifications on this only when there's a direct method call
                ForwardEvaluationInfo forward = fr.scopeIsThis()
                        ? builder.notNullNotAssignment().build()
                        : builder.ensureModificationSetNotNull().build();
                return ve.evaluate(context, forward);
            }
            if (forwardEvaluationInfo.isEvaluatingFieldExpression()) {
                // the field analyser does not know local variables, so no need for assignments
                return new EvaluationResultImpl.Builder(context).setExpression(fr.scope()).build();
            }
            assert fr.scopeVariable() instanceof LocalVariableReference lvr
                    && lvr.variableNature() instanceof VariableNature.ScopeVariable;
            assert fr.scope() != null;
            ForwardEvaluationInfo forward = builder.ensureModificationSetNotNull().build();
            VariableExpression scopeVE = new VariableExpression(fr.scope().getIdentifier(), fr.scopeVariable());
            Assignment assignment = new Assignment(context.getPrimitives(), scopeVE, fr.scope());
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
    public static EvaluationResult evaluateForArray(EvaluationResult context,
                                                    ForwardEvaluationInfo forwardEvaluationInfo,
                                                    Expression expression,
                                                    Variable variable,
                                                    boolean increaseCnn) {
        if (expression.isConstant()) {
            return new EvaluationResultImpl.Builder(context).setExpression(expression).build();
        }
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            DV cnn = forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL);
            DV higher = increaseCnn ? MultiLevel.composeOneLevelMoreNotNull(cnn) : cnn;
            return ve.evaluate(context, forwardEvaluationInfo.copy().notNullNotAssignment(higher).build());
        }
        assert variable instanceof LocalVariableReference lvr
                && lvr.variableNature() instanceof VariableNature.ScopeVariable;
        ForwardEvaluationInfo forward = forwardEvaluationInfo.copy().ensureModificationSet().build();
        VariableExpression scopeVE = new VariableExpression(expression.getIdentifier(), variable);
        Assignment assignment = new Assignment(context.getPrimitives(), scopeVE, expression);
        EvaluationResult er = assignment.evaluate(context, forward);
        EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context).compose(er);
        // we have to set the not-null context for the array variable, because there is array access
        builder.variableOccursInNotNullContext(variable, expression, MultiLevel.EFFECTIVELY_NOT_NULL_DV, forwardEvaluationInfo);
        return builder.build();
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        return internalLinkedVariables(variable, LinkedVariables.LINK_STATICALLY_ASSIGNED);
    }

    static LinkedVariables internalLinkedVariables(Variable variable, DV linkLevel) {
        if (variable instanceof DependentVariable dv) {
            LinkedVariables recursive = internalLinkedVariables(dv.arrayVariable(), LinkedVariables.LINK_IS_HC_OF);
            return LinkedVariables.of(variable, linkLevel).merge(recursive);
        }
        if (variable instanceof FieldReference fr && fr.scopeVariable() != null) {
            LinkedVariables recursive = internalLinkedVariables(fr.scopeVariable(), LinkedVariables.LINK_IS_HC_OF);
            return LinkedVariables.of(variable, linkLevel).merge(recursive);
        }
        return LinkedVariables.of(variable, linkLevel);
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
    public List<Variable> variables(DescendMode descendIntoFieldReferences) {
        if (descendIntoFieldReferences != DescendMode.NO && variable instanceof FieldReference fr && !fr.isStatic()) {
            if (descendIntoFieldReferences == DescendMode.YES_INCLUDE_THIS || !fr.scopeIsThis()) {
                Stream<Variable> scopeVarStream = fr.scopeVariable() == null ? Stream.of() : Stream.of(fr.scopeVariable());
                Stream<Variable> value = scopeValue.variables(descendIntoFieldReferences).stream();
                return Stream.concat(Stream.concat(scopeVarStream, value), Stream.of(variable)).toList();
            }
        }
        return List.of(variable);
    }

    @Override
    public List<Variable> variablesWithoutCondition() {
        if (variable instanceof FieldReference fr && !fr.scopeIsThis()) {
            return ListUtil.concatImmutable(fr.scope().variablesWithoutCondition(), List.of(variable));
        }
        return List.of(variable);
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (variable instanceof FieldReference fr && !fr.isStatic()) {
            if (!fr.isDefaultScope()) {
                outputBuilder.add(outputInParenthesis(qualification, precedence(), scopeValue)).add(Symbol.DOT);
            }
            outputBuilder.add(new Text(fr.fieldInfo().name));
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
    public PackedIntMap<TypeInfo> typesReferenced2(PackedInt weight) {
        return variable.typesReferenced2(weight);
    }

    @Override
    public boolean hasState() {
        throw new UnsupportedOperationException("Use evaluationContext.hasState()");
    }

    @Override
    public List<? extends Element> subElements() {
        if (variable instanceof FieldReference fr && !fr.scopeIsThis()) {
            return List.of(fr.scope());
        }
        return List.of();
    }

    public static Expression tryShortCut(EvaluationResult context, Expression scopeValue, FieldReference fr) {
        ConstructorCall constructorCall;
        if ((constructorCall = scopeValue.asInstanceOf(ConstructorCall.class)) != null && constructorCall.constructor() != null) {
            return extractNewObject(context, constructorCall, fr.fieldInfo());
        }
        if (scopeValue instanceof VariableExpression scopeVe && scopeVe.variable instanceof FieldReference scopeFr) {
            FieldAnalysis fieldAnalysis = context.getAnalyserContext().getFieldAnalysis(scopeFr.fieldInfo());
            Expression efv = fieldAnalysis.getValue();
            ConstructorCall cc2;
            if (efv != null && (cc2 = efv.asInstanceOf(ConstructorCall.class)) != null && cc2.constructor() != null) {
                return extractNewObject(context, cc2, fr.fieldInfo());
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
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
            if (!parameterAnalysis.isAssignedToFieldDelaysResolved()) {
                causes = causes.merge(parameterAnalysis.assignedToFieldDelays());
            } else {
                Map<FieldInfo, DV> assigned = parameterAnalysis.getAssignedToField();
                if (assigned != null) {
                    DV assignedOrLinked = assigned.get(fieldInfo);
                    if (assignedOrLinked != null && LinkedVariables.isAssigned(assignedOrLinked)) {
                        return constructorCall.getParameterExpressions().get(i);
                    }
                }
            }
            i++;
        }
        if (causes.isDelayed()) {
            // worth waiting
            return DelayedVariableExpression.forField(new FieldReferenceImpl(context.getAnalyserContext(), fieldInfo), 0, causes);
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
    public Either<CausesOfDelay, Set<Variable>> loopSourceVariables(AnalyserContext analyserContext, ParameterizedType parameterizedType) {
        return loopSourceVariables(analyserContext, variable, variable.parameterizedType(), parameterizedType);
    }

    // also in DelayedVariableExpression
    public static Either<CausesOfDelay, Set<Variable>> loopSourceVariables(AnalyserContext analyserContext,
                                                                           Variable variable,
                                                                           ParameterizedType myType,
                                                                           ParameterizedType parameterizedType) {
        if (myType.arrays > 0 && myType.copyWithOneFewerArrays().equals(parameterizedType)) {
            return Either.right(Set.of(variable));
        }
        ParameterizedType typeParameterOfIterable = myType.typeInfo == null ? null
                : typeParameterOfIterable(analyserContext, myType); // String, because Set<String> <- Iterable<String>
        if (typeParameterOfIterable != null && typeParameterOfIterable.isAssignableFrom(analyserContext, parameterizedType)) {
            return Either.right(Set.of(variable));
        }
        return EvaluationContext.NO_LOOP_SOURCE_VARIABLES;
    }

    private static ParameterizedType typeParameterOfIterable(AnalyserContext analyserContext,
                                                             ParameterizedType concreteType) {
        ParameterizedType iterablePt = analyserContext.importantClasses().iterable();
        ParameterizedType concreteIterable = concreteType.concreteSuperType(analyserContext, iterablePt);
        return concreteIterable == null ? null : concreteIterable.parameters.get(0);
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
