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
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.annotation.E2Container;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@E2Container
public class DelayedVariableExpression extends CommonVariableExpression {
    public final String msg;
    public final String debug;
    public final Variable variable;
    public final CausesOfDelay causesOfDelay;
    public final int statementTime;

    private DelayedVariableExpression(String msg,
                                      String debug,
                                      Variable variable,
                                      int statementTime,
                                      CausesOfDelay causesOfDelay) {
        super(Identifier.CONSTANT);
        this.msg = msg;
        this.debug = debug;
        this.statementTime = statementTime;
        this.causesOfDelay = causesOfDelay;
        assert causesOfDelay.causesStream().noneMatch(cause -> cause.cause() == CauseOfDelay.Cause.MIN_INT)
                : "Causes of delay: " + causesOfDelay;
        assert causesOfDelay.isDelayed();
        this.variable = variable;
    }

    public static DelayedVariableExpression forParameter(ParameterInfo parameterInfo,
                                                         CausesOfDelay causesOfDelay) {
        return new DelayedVariableExpression("<p:" + parameterInfo.name + ">",
                "<parameter:" + parameterInfo.fullyQualifiedName() + ">", parameterInfo,
                VariableInfoContainer.NOT_A_FIELD, causesOfDelay);
    }

    public static DelayedVariableExpression forField(FieldReference fieldReference,
                                                     int statementTime,
                                                     CauseOfDelay causeOfDelay) {
        return forField(fieldReference, statementTime, new SimpleSet(causeOfDelay));
    }

    public static DelayedVariableExpression forBreakingInitialisationDelay(FieldReference fieldReference,
                                                                           int statementTime,
                                                                           CauseOfDelay causeOfDelay) {
        return new DelayedVariableExpression("<f*:" + fieldReference.fieldInfo.name + ">",
                "<field*:" + fieldReference.fullyQualifiedName() + ">", fieldReference, statementTime, new SimpleSet(causeOfDelay));
    }

    public static DelayedVariableExpression forField(FieldReference fieldReference,
                                                     int statementTime,
                                                     CausesOfDelay causesOfDelay) {
        return new DelayedVariableExpression("<f:" + fieldReference.fieldInfo.name + ">",
                "<field:" + fieldReference.fullyQualifiedName() + ">", fieldReference, statementTime, causesOfDelay);
    }

    public static Expression forVariable(Variable variable, int statementTime, CausesOfDelay causesOfDelay) {
        if (variable instanceof FieldReference fieldReference)
            return forField(fieldReference, statementTime, causesOfDelay);
        if (variable instanceof ParameterInfo parameterInfo) return forParameter(parameterInfo, causesOfDelay);
        return new DelayedVariableExpression("<v:" + variable.simpleName() + ">",
                "<variable:" + variable.fullyQualifiedName() + ">", variable, VariableInfoContainer.NOT_A_FIELD, causesOfDelay);
    }


    public static Expression forLocalVariableInLoop(Variable variable, CausesOfDelay causesOfDelay) {
        String msg = "<vl:" + variable.simpleName() + ">";
        return new DelayedVariableExpression(msg, msg, variable, VariableInfoContainer.NOT_A_FIELD, causesOfDelay);
    }

    public static Expression forDelayedValueProperties(Variable variable, int statementTime, CausesOfDelay causesOfDelay) {
        String msg = "<vp:" + variable.simpleName() + ":" + causesOfDelay + ">";
        return new DelayedVariableExpression(msg, msg, variable, statementTime, causesOfDelay);
    }

    public static Expression forDelayedModificationInMethodCall(Variable variable, CausesOfDelay causesOfDelay) {
        String msg = "<mmc:" + variable.simpleName() + ">";
        return new DelayedVariableExpression(msg, msg, variable, variable.statementTime(), causesOfDelay);
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
        return new OutputBuilder().add(new Text(msg, debug));
    }

    @Override
    public Precedence precedence() {
        return Precedence.TOP;
    }

    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfo) {
        // CONTEXT NOT NULL as soon as possible, also for delayed values...

        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);

        if (variable instanceof FieldReference fr && fr.scope != null) {
            // do not continue modification onto This: we want modifications on this only when there's a direct method call
            ForwardEvaluationInfo forward = fr.scopeIsThis() ? forwardEvaluationInfo.notNullNotAssignment() :
                    forwardEvaluationInfo.copyModificationEnsureNotNull();
            EvaluationResult scopeResult = fr.scope.evaluate(context, forward);
            builder.compose(scopeResult);
        }

        DV cnn = forwardEvaluationInfo.getProperty(Property.CONTEXT_NOT_NULL);
        if (cnn.gt(MultiLevel.NULLABLE_DV)) {
            builder.variableOccursInNotNullContext(variable, this, cnn);
        }
        return builder.setExpression(this).build();
    }

    @Override
    public DV getProperty(EvaluationResult context, Property property, boolean duringEvaluation) {
        return causesOfDelay;
    }

    // special treatment because of == equality.
    @Override
    public Expression translate(TranslationMap translationMap) {
        Expression expression = translationMap.translateVariableExpressionNullIfNotTranslated(variable);
        return Objects.requireNonNullElse(expression, this);
    }

    // special treatment because of == equality
    @Override
    public EvaluationResult reEvaluate(EvaluationResult context, Map<Expression, Expression> translation) {
        Optional<Map.Entry<Expression, Expression>> found = translation.entrySet().stream()
                .filter(e -> e.getKey() instanceof VariableExpression ve && ve.variable().equals(variable))
                .findFirst();
        Expression result;
        if (found.isPresent()) {
            result = found.get().getValue();
        } else if (variable instanceof FieldReference fr && fr.scope != null) {
            EvaluationResult reEval = fr.scope.reEvaluate(context, translation); // recurse
            Expression replaceScope = reEval.getExpression();
            if (!replaceScope.equals(fr.scope)) {
                FieldReference newRef = new FieldReference(InspectionProvider.DEFAULT, fr.fieldInfo, replaceScope);
                result = new DelayedVariableExpression(msg, debug, newRef, statementTime, causesOfDelay);
            } else {
                result = this;
            }
        } else {
            result = this;
        }
        return new EvaluationResult.Builder(context).setExpression(result).build();
    }

    @Override
    public LinkedVariables linkedVariables(EvaluationResult context) {
        return new LinkedVariables(Map.of(variable, causesOfDelay));
    }

    @Override
    public List<Variable> variables(boolean descendIntoFieldReferences) {
        if (descendIntoFieldReferences && variable instanceof FieldReference fr && fr.scope != null && !fr.scopeIsThis()) {
            return ListUtil.concatImmutable(List.of(variable), fr.scope.variables(true));
        }
        return List.of(variable);
    }

    @Override
    public CausesOfDelay causesOfDelay() {
        return causesOfDelay;
    }

    @Override
    public Expression mergeDelays(CausesOfDelay causesOfDelay) {
        return new DelayedVariableExpression(msg, debug, variable, statementTime, this.causesOfDelay.merge(causesOfDelay));
    }
}
