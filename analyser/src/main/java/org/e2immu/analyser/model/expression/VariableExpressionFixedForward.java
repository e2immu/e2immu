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
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.InspectionProvider;

/*
Specific use in ECI (see SAInitializersAndUpdaters)
 */
public class VariableExpressionFixedForward extends VariableExpression {
    private final ForwardEvaluationInfo forwardEvaluationInfo;

    public VariableExpressionFixedForward(ParameterInfo parameterInfo, ForwardEvaluationInfo forwardEvaluationInfo) {
        super(parameterInfo.identifier, parameterInfo);
        this.forwardEvaluationInfo = forwardEvaluationInfo;
    }

    @Override
    public ForwardEvaluationInfo overrideForward(ForwardEvaluationInfo asParameter) {
        return forwardEvaluationInfo;
    }

    /*
    the value is being delayed in SAApply.delayAssignmentValue()
     */
    @Override
    public EvaluationResult evaluate(EvaluationResult context, ForwardEvaluationInfo forwardEvaluationInfoIn) {
        EvaluationResult result = super.evaluate(context, forwardEvaluationInfoIn);
        if (context.evaluationContext().getIteration() == 0) {
            EvaluationResultImpl.Builder builder = new EvaluationResultImpl.Builder(context);
            VariableCause cause = new VariableCause(variable(), context.evaluationContext().getLocation(Stage.EVALUATION),
                    CauseOfDelay.Cause.INITIAL_VALUE);
            CausesOfDelay causes = DelayFactory.createDelay(cause);
            builder.link(variable(), new This(InspectionProvider.DEFAULT, context.getCurrentType()), causes);
            return builder.compose(result).build();
        }
        return result;
    }
}
