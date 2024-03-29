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
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.impl.BaseExpression;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.E2Container;
import org.e2immu.support.Either;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.e2immu.analyser.model.expression.ArrayAccess.ARRAY_VARIABLE;
import static org.e2immu.analyser.model.expression.ArrayAccess.INDEX_VARIABLE;

@E2Container
public class DelayedVariableExpression extends BaseExpression implements IsVariableExpression {
    public final String msg;
    public final String fqn;
    public final Variable variable;
    public final CausesOfDelay causesOfDelay;
    public final int statementTime;

    private DelayedVariableExpression(String msg,
                                      Variable variable,
                                      int statementTime,
                                      CausesOfDelay causesOfDelay) {
        super(Identifier.constant(variable.fullyQualifiedName() + ":" + statementTime));
        this.msg = msg;
        this.fqn = "<" + variable.fullyQualifiedName() + ":" + statementTime + ">";
        this.statementTime = statementTime;
        this.causesOfDelay = causesOfDelay;
        assert causesOfDelay.causesStream().noneMatch(cause -> cause.cause() == CauseOfDelay.Cause.MIN_INT)
                : "Causes of delay: " + causesOfDelay;
        assert causesOfDelay.isDelayed();
        this.variable = variable;
    }

    public static DelayedVariableExpression forParameter(ParameterInfo parameterInfo,
                                                         CausesOfDelay causesOfDelay) {
        return new DelayedVariableExpression("<p:" + parameterInfo.name + ">", parameterInfo,
                VariableInfoContainer.NOT_A_FIELD, causesOfDelay);
    }

    public static DelayedVariableExpression forField(FieldReference fieldReference,
                                                     int statementTime,
                                                     CauseOfDelay causeOfDelay) {
        return forField(fieldReference, statementTime, DelayFactory.createDelay(causeOfDelay));
    }

    public static DelayedVariableExpression forBreakingInitialisationDelay(FieldReference fieldReference,
                                                                           int statementTime,
                                                                           CauseOfDelay causeOfDelay) {
        return new DelayedVariableExpression("<f*:" + fieldString(fieldReference) + ">", fieldReference,
                statementTime, DelayFactory.createDelay(causeOfDelay));
    }

    public static DelayedVariableExpression forField(FieldReference fieldReference,
                                                     int statementTime,
                                                     CausesOfDelay causesOfDelay) {
        return new DelayedVariableExpression("<f:" + fieldString(fieldReference) + ">", fieldReference,
                statementTime, causesOfDelay);
    }

    private static String fieldString(FieldReference fieldReference) {
        String scopeString = fieldReference.isDefaultScope || fieldReference.isStatic ? "" : fieldReference.scope.minimalOutput() + ".";
        return scopeString + fieldReference.fieldInfo.name;
    }

    public static Expression forVariable(Variable variable, int statementTime, CausesOfDelay causesOfDelay) {
        if (variable instanceof FieldReference fieldReference)
            return forField(fieldReference, statementTime, causesOfDelay);
        if (variable instanceof ParameterInfo parameterInfo) return forParameter(parameterInfo, causesOfDelay);
        return new DelayedVariableExpression("<v:" + variable.simpleName() + ">", variable,
                VariableInfoContainer.NOT_A_FIELD, causesOfDelay);
    }


    public static Expression forLocalVariableInLoop(Variable variable, CausesOfDelay causesOfDelay) {
        String msg = "<vl:" + variable.simpleName() + ">";
        return new DelayedVariableExpression(msg, variable, VariableInfoContainer.NOT_A_FIELD, causesOfDelay);
    }

    public static Expression forDelayedValueProperties(Variable variable, int statementTime, CausesOfDelay causesOfDelay) {
        String msg = "<vp:" + variable.simpleName() + ":" + causesOfDelay + ">";
        return new DelayedVariableExpression(msg, variable, statementTime, causesOfDelay);
    }

    public static Expression forDelayedModificationInMethodCall(Variable variable, CausesOfDelay causesOfDelay) {
        String msg = "<mmc:" + variable.simpleName() + ">";
        return new DelayedVariableExpression(msg, variable, variable.statementTime(), causesOfDelay);
    }

    public static Expression forMerge(Variable variable, CausesOfDelay causes) {
        String msg = "<merge:" + variable.simpleName() + ">";
        return new DelayedVariableExpression(msg, variable, variable.statementTime(), causes);
    }

    public static Expression forDependentVariable(DependentVariable dv, CausesOfDelay causesOfDelay) {
        String msg = "<dv:" + dv.simpleName + ">";
        return new DelayedVariableExpression(msg, dv, dv.statementTime(), causesOfDelay);
    }

    @Override
    public void visit(Predicate<Element> predicate) {
        predicate.test(this);
    }

    /*
    variable fields have different values according to statement time, but then, at this point we cannot know yet
    whether the field will be variable or not.
    Basics7 shows a case where the local condition manager goes from true to false depending on this equality.

    requires special translate and re-evaluate!
     */

    @Override
    public boolean equals(Object o) {
        return o instanceof DelayedVariableExpression dve
                && variable.equals(dve.variable)
                && statementTime == dve.statementTime;
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_VARIABLE;
    }

    // code very similar to that in VariableExpression; that's important, because we need to keep them closely
    // aligned AND compatible with equals: sometimes we use TreeMaps, sometimes we use HashMaps.
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
            int c = variableId().compareTo(ive.variableId());
            if (c == 0) {
                DelayedVariableExpression dve;
                if ((dve = e.asInstanceOf(DelayedVariableExpression.class)) != null) {
                    return statementTime - dve.statementTime;
                }
                // same variable, but the other one is not delayed
                return 1; // I come last!
            }
            return c;
        }
        throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
        return variable.hashCode() + 31 * statementTime;
    }

    @Override
    public Variable variable() {
        return variable;
    }

    @Override
    public boolean isNumeric() {
        return variable.parameterizedType().isNumeric();
    }

    @Override
    public String toString() {
        return msg;
    }

    @Override
    public ParameterizedType returnType() {
        return variable.parameterizedType();
    }

    @Override
    public OutputBuilder output(Qualification qualification) {
        if (qualification == Qualification.FULLY_QUALIFIED_NAME) {
            return new OutputBuilder().add(new Text(fqn));
        }
        return new OutputBuilder().add(new Text(msg));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        // CONTEXT NOT NULL as soon as possible, also for delayed values...

        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);

        if (variable instanceof FieldReference fr) {
            // do not continue modification onto This: we want modifications on this only when there's a direct method call
            ForwardEvaluationInfo forward = fr.scopeIsThis()
                    ? forwardEvaluationInfo.copy().notNullNotAssignment().build()
                    : forwardEvaluationInfo.copy().ensureModificationSetNotNull().build();
            EvaluationResult scopeResult = fr.scope.evaluate(context, forward);
            builder.compose(scopeResult);
        }

        DV cnn = forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL);
        if (cnn.gt(MultiLevel.NULLABLE_DV)) {
            builder.variableOccursInNotNullContext(variable, this, cnn, forwardEvaluationInfo);
        }

        if (!forwardEvaluationInfo.isAssignmentTarget()) {
            builder.markRead(variable);
        }
        return builder.setExpression(this).build();
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return causesOfDelay;
    }

    // special treatment because of == equality.
    @Override
    public Expression translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Expression expression = translationMap.translateVariableExpressionNullIfNotTranslated(variable);
        if (expression != null) {
            // we do our best to take the replacement variable, if we can reach it. needed for parameter replacements in ECI
            // we'd rather not replace a DVE with a VE
            IsVariableExpression ive;
            if (expression.isDone() && (ive = expression.asInstanceOf(IsVariableExpression.class)) != null) {
                return new DelayedVariableExpression("<dv:" + ive.variable() + ">", ive.variable(), statementTime, causesOfDelay);
            }
            return expression;
        }
        // unlike in merge, in the case of ExplicitConstructorInvocation, we cannot predict which fields need their scope translating
        if (translationMap.recurseIntoScopeVariables()) {
            if (variable instanceof FieldReference fr) {
                Expression translated = fr.scope.translate(inspectionProvider, translationMap);
                if (translated != fr.scope) {
                    FieldReference newFr = new FieldReference(inspectionProvider, fr.fieldInfo, translated, fr.getOwningType());
                    return DelayedVariableExpression.forField(newFr, statementTime, causesOfDelay);
                }
            } else if (variable instanceof DependentVariable dv) {
                Expression translatedArray = dv.arrayExpression().translate(inspectionProvider, translationMap);
                Expression translatedIndex = dv.indexExpression().translate(inspectionProvider, translationMap);
                if (translatedArray != dv.arrayExpression() || translatedIndex != dv.indexExpression()) {
                    Variable av = ArrayAccess.makeVariable(translatedArray, translatedArray.getIdentifier(), ARRAY_VARIABLE, dv.getOwningType());
                    Variable iv = ArrayAccess.makeVariable(translatedIndex, translatedIndex.getIdentifier(), INDEX_VARIABLE, dv.getOwningType());
                    DependentVariable newDv = new DependentVariable(dv.getIdentifier(), translatedArray,
                            Objects.requireNonNull(av), translatedIndex, iv, dv.parameterizedType, dv.statementIndex);
                    return DelayedVariableExpression.forDependentVariable(newDv, causesOfDelay);
                }
            }
        }
        return this;
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        return LinkedVariables.of(variable, causesOfDelay);
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        if (descendIntoFieldReferences) {
            if (variable instanceof FieldReference fr) {
                return ListUtil.concatImmutable(List.of(variable), fr.scope.variables(true));
            }
            if (variable instanceof DependentVariable dv) {
                return Stream.concat(Stream.concat(Stream.of(variable),
                                dv.arrayExpression().variables(true).stream()),
                        dv.indexExpression().variables(true).stream()).toList();
            }
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
    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        return new DelayedVariableExpression(msg, variable, statementTime, this.causesOfDelay.merge(causesOfDelay));
    }

    @Override
    public Either<CausesOfDelay, Set<Variable>> loopSourceVariables(AnalyserContext analyserContext,
                                                                    ParameterizedType parameterizedType) {
        return VariableExpression.loopSourceVariables(analyserContext, variable, variable.parameterizedType(),
                parameterizedType);
    }

    @Override
    public Set<Variable> directAssignmentVariables() {
        return Set.of(variable);
    }
}
