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

package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.VariableInfoContainer;
import org.e2immu.analyser.analyser.util.FindInstanceOfPatterns;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.Assignment;
import org.e2immu.analyser.model.expression.IsVariableExpression;
import org.e2immu.analyser.model.expression.PropertyWrapper;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.IfElseStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;

import java.util.List;

record SAPatternVariable(StatementAnalysis statementAnalysis) {

    /*
    create local variables 'YYY y = x' for every sub-expression x instanceof YYY y

    the scope of the variable is determined as follows:
    (1) if the expression is an if-statement, without else: pos = then block, neg = rest of current block
    (2) if the expression is an if-statement with else: pos = then block, neg = else block
    (3) otherwise, only the current expression is accepted (we set to then block)
    ==> positive: always then block
    ==> negative: either else or rest of block
     */


    List<Assignment> patternVariables(EvaluationContext evaluationContext, Expression expression) {
        List<FindInstanceOfPatterns.InstanceOfPositive> instanceOfList = FindInstanceOfPatterns.find(expression);
        boolean haveElse = statementAnalysis.statement() instanceof IfElseStatement ifElse && !ifElse.elseBlock.isEmpty();
        StatementAnalysis firstSubBlock = !(statementAnalysis.statement() instanceof IfElseStatement) ? null :
                statementAnalysis.navigationData().blocks.get().get(0).orElse(null);
        // create local variables
        String index = statementAnalysis.index();
        instanceOfList.stream()
                .filter(instanceOf -> instanceOf.instanceOf().patternVariable() != null)
                .filter(instanceOf -> !statementAnalysis.variableIsSet(instanceOf.instanceOf().patternVariable().simpleName()))
                .forEach(instanceOf -> {
                    LocalVariableReference lvr = instanceOf.instanceOf().patternVariable();
                    String scope = instanceOf.positive() ? index + ".0.0" : haveElse ? index + ".1.0" :
                            indexOnlyIfEscapeInSubBlock(firstSubBlock);
                    Variable scopeVariable = instanceOf.instanceOf().expression() instanceof IsVariableExpression ve ?
                            ve.variable() : null;
                    VariableNature variableNature = new VariableNature.Pattern(scope, instanceOf.positive(), scopeVariable);
                    statementAnalysis.createVariable(evaluationContext, lvr, VariableInfoContainer.IGNORE_STATEMENT_TIME,
                            variableNature);
                });

        // add assignments
        // the reason we add the property wrapper to the expression is shown in InstanceOf_9: in the case
        // of a cast from Object to String, we move from an object not guaranteed to be @Container (Object) to one
        // that is. When merging back, the "object" value of "string" still needs to have the @Container property

        // the reason we don't add a Cast(...) is that a Cast does not implement IsVariableExpression,
        // which, among others, is needed in Assignment to catch the linked variables to others.
        return instanceOfList.stream()
                .filter(iop -> iop.instanceOf().patternVariable() != null)
                .map(iop -> new Assignment(evaluationContext.getPrimitives(),
                        new VariableExpression(iop.instanceOf().identifier, iop.instanceOf().patternVariable()),
                        PropertyWrapper.propertyWrapper(iop.instanceOf().expression(), null,
                                iop.instanceOf().parameterizedType())))
                .toList();
    }

    private String indexOnlyIfEscapeInSubBlock(StatementAnalysis subBlock) {
        if (subBlock == null) return "xx";
        // TODO no idea how to implement... (could be a delay, we have no idea yet because sub-blocks are handled later
        // and we cannot overwrite VIC's variableNature)
        return statementAnalysis.navigationData().next.get().map(StatementAnalysis::index).orElse("xx");
    }
}
