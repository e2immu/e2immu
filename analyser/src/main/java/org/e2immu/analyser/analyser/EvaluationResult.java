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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.parser.Primitives;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/*
Contains all side effects of analysing an expression.
The 'apply' method of the analyser executes them.

It contains:

- an increased statement time, caused by calls to those methods that increase time
- the computed result (value)
- a sequence of stored result, for CommaExpressions and explicit constructor invocations
- an assembled precondition, gathered from calls to methods that have preconditions
- a map of value changes (and linked var, property, ...)
- a list of error messages
- a list of object flows (for later)

Critically, the apply method will execute the value changes before executing the modifications.
Important value changes are:
- a variable has been read at a given statement time
- a variable has been assigned a value
- the linked variables of a variable have been computed

We track delays in state change


EvaluatedExpressionCache will be stored in StatementAnalysis.stateData() for use by CodeModernizer.

 */
public interface EvaluationResult extends Comparable<EvaluationResult> {

    EvaluationContext evaluationContext();

    Expression value();

    List<Expression> storedValues();

    CausesOfDelay causesOfDelay();

    Messages messages();

    Map<Variable, ChangeData> changeData();

    Precondition precondition();


    EvaluationResult copy(EvaluationContext evaluationContext);

    AnalyserContext getAnalyserContext();

    Primitives getPrimitives();

    TypeInfo getCurrentType();

    MethodAnalyser getCurrentMethod();

    // for debugger
    String safeMethodName();


    Stream<Message> getMessageStream();

    Stream<Map.Entry<Variable, ChangeData>> getExpressionChangeStream();

    Expression getExpression();

    /*
    This version of the method redirects to the original, after checking with immediate CNN on variables.
    Example: 'a.x() && a != null' will simplify to 'a.x()', as 'a' will have CNN=5 after evaluating the first part
    of the expression.
     */

    DV isNotNull0(boolean useEnnInsteadOfCnn, ForwardEvaluationInfo forwardEvaluationInfo);

    Expression currentValue(Variable variable);

    EvaluationResult child(Expression condition, Set<Variable> conditionVariables);

    EvaluationResult child(Expression condition, Set<Variable> conditionVariables, boolean disableEvaluationOfMethodCallsUsingCompanionMethods);

    EvaluationResult copyToPreventAbsoluteStateComputation();

    EvaluationResult childState(Expression state, Set<Variable> stateVariables);

    EvaluationResult translate(TranslationMap translationMap);

    EvaluationResult withNewEvaluationContext(EvaluationContext newEc);

    LinkedVariables linkedVariables(Variable variable);

    EvaluationResult filterChangeData(Predicate<Variable> predicate);

    DV containsModification();

    int statementTime();

    String modificationTimesOf(LinkedVariables... values);

    String modificationTimesOf(LinkedVariables lvOfObject, List<LinkedVariables> lvOfParameters);

    Map<Variable, Integer> modificationTimeIncrements();

    DV getProperty(Expression expression, Property property);

    EvaluationResult withExtraChangeData(Variable variable, ChangeData cd);

    LinkedVariables linkedVariablesOfExpression();

    @Override
    default int compareTo(EvaluationResult o) {
        return getExpression().compareTo(o.getExpression());
    }

    default ChangeData findChangeData(String name) {
        return changeData().entrySet().stream().filter(e -> name.equals(e.getKey().simpleName()))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
    }
}
