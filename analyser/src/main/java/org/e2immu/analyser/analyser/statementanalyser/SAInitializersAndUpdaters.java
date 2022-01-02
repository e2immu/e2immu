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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.nonanalyserimpl.VariableInfoContainerImpl;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.impl.StatementAnalysisImpl;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.Statement;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.statement.ExplicitConstructorInvocation;
import org.e2immu.analyser.model.statement.LoopStatement;
import org.e2immu.analyser.model.statement.Structure;
import org.e2immu.analyser.model.statement.TryStatement;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.VariableNature;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.e2immu.analyser.analyser.Property.*;

record SAInitializersAndUpdaters(StatementAnalysis statementAnalysis) {

    private String index() {
        return statementAnalysis.index();
    }

    private Location location() {
        return statementAnalysis.location();
    }

    private Statement statement() {
        return statementAnalysis.statement();
    }

    /*
    we create the variable(s), to make sure they exist in INIT level, but defer computation of their value to evaluation.
    In effect, we split int i=3; into int i (INIT); i=3 (EVAL);

    Loop and catch variables are special in that their scope is restricted to the statement and its block.
    We deal with them here, however they are assigned in the structure.

    Explicit constructor invocation uses "updaters" in the structure, but that is essentially level 3 evaluation.

    The for-statement has explicit initialisation and updating. These statements need evaluation, but the actual
    values are only used for independent for-loop analysis (not yet implemented) rather than for assigning real
    values to the loop variable.

    Loop (and catch) variables will be defined in level 2. A special local variable with a $<index> suffix will
    be created to represent a generic loop value.

    The special thing about creating variables at level 2 in a statement is that they are not transferred to the next statement,
    nor are they merged into level 4.
     */
    List<Expression> initializersAndUpdaters(ForwardAnalysisInfo forwardAnalysisInfo, EvaluationContext evaluationContext) {
        List<Expression> expressionsToEvaluate = new ArrayList<>();
        AnalyserContext analyserContext = evaluationContext.getAnalyserContext();

        // part 1: Create a local variable x for(X x: Xs) {...}, or in catch(Exception e), or for(int i=...), or int i=3, j=4;
        // variable will be set to a NewObject case of a catch

        if (forwardAnalysisInfo.catchVariable() != null) {
            // inject a catch(E1 | E2 e) { } exception variable, directly with assigned value, "read"
            LocalVariableCreation catchVariable = forwardAnalysisInfo.catchVariable();
            String name = catchVariable.localVariable.name();
            if (!statementAnalysis.variableIsSet(name)) {
                LocalVariableReference lvr = new LocalVariableReference(catchVariable.localVariable);
                VariableInfoContainer vic = VariableInfoContainerImpl.newCatchVariable(location(), lvr, index(),
                        Instance.forCatchOrThis(index(), lvr, analyserContext),
                        analyserContext.defaultImmutable(lvr.parameterizedType(), false),
                        statementAnalysis.navigationData().hasSubBlocks());
                ((StatementAnalysisImpl) statementAnalysis).putVariable(name, vic);
            }
        }

        for (Expression expression : statementAnalysis.statement().getStructure().initialisers()) {
            Expression initialiserToEvaluate;

            if (expression instanceof LocalVariableCreation lvc) {
                LocalVariableReference lvr;
                VariableInfoContainer vic;
                String name = lvc.localVariable.name();
                if (!statementAnalysis.variableIsSet(name)) {

                    // create the local (loop) variable

                    lvr = new LocalVariableReference(lvc.localVariable);
                    VariableNature variableNature;
                    if (statement() instanceof LoopStatement) {
                        variableNature = new VariableNature.LoopVariable(index());
                    } else if (statement() instanceof TryStatement) {
                        variableNature = new VariableNature.TryResource(index());
                    } else {
                        variableNature = new VariableNature.NormalLocalVariable(index());
                    }
                    vic = statementAnalysis.createVariable(evaluationContext,
                            lvr, VariableInfoContainer.NOT_A_VARIABLE_FIELD, variableNature);
                    if (statement() instanceof LoopStatement) {
                        ((StatementAnalysisImpl) statementAnalysis).ensureLocalVariableAssignedInThisLoop(lvr.fullyQualifiedName());
                    }
                } else {
                    vic = statementAnalysis.getVariable(name);
                    lvr = (LocalVariableReference) vic.current().variable();
                }

                // what should we evaluate? catch: assign a value which will be read; for(int i=0;...) --> 0 instead of i=0;
                if (statement() instanceof LoopStatement) {
                    initialiserToEvaluate = lvc.expression;
                    // but, because we don't evaluate the assignment, we need to assign some value to the loop variable
                    // otherwise we'll get delays
                    // especially in the case of forEach, the lvc.expression is empty (e.g., 'String s') anyway
                    // an assignment may be difficult. The value is never used, only local copies are

                    DV defaultImmutable = analyserContext.defaultImmutable(lvr.parameterizedType(), false);
                    DV initialNotNull = AnalysisProvider.defaultNotNull(lvr.parameterizedType());
                    Map<Property, DV> properties =
                            Map.of(CONTEXT_MODIFIED, DV.FALSE_DV,
                                    EXTERNAL_NOT_NULL, MultiLevel.NOT_INVOLVED_DV,
                                    CONTEXT_NOT_NULL, initialNotNull,
                                    EXTERNAL_IMMUTABLE, MultiLevel.NOT_INVOLVED_DV,
                                    CONTEXT_IMMUTABLE, defaultImmutable);
                    Map<Property, DV> valueProperties = Map.of(); // FIXME
                    Instance instance = Instance.forLoopVariable(index(), lvr, valueProperties);
                    vic.setValue(instance, LinkedVariables.EMPTY, properties, true);
                    // the linking (normal, and content) can only be done after evaluating the expression over which we iterate
                } else {
                    initialiserToEvaluate = lvc; // == expression
                }
            } else initialiserToEvaluate = expression;

            if (initialiserToEvaluate != null && initialiserToEvaluate != EmptyExpression.EMPTY_EXPRESSION) {
                expressionsToEvaluate.add(initialiserToEvaluate);
            }
        }

        // part 2: updaters, + determine which local variables are modified in the updaters

        if (statementAnalysis.statement() instanceof LoopStatement) {
            for (Expression expression : statementAnalysis.statement().getStructure().updaters()) {
                expression.visit(e -> {
                    VariableExpression ve;
                    if (e instanceof Assignment assignment
                            && (ve = assignment.target.asInstanceOf(VariableExpression.class)) != null) {
                        ((StatementAnalysisImpl) statementAnalysis).ensureLocalVariableAssignedInThisLoop(ve.name());
                        expressionsToEvaluate.add(assignment); // we do evaluate the assignment no that the var will be there
                    }
                });
            }
        } else if (statementAnalysis.statement() instanceof ExplicitConstructorInvocation) {
            Structure structure = statement().getStructure();
            expressionsToEvaluate.addAll(structure.updaters());
        }

        return expressionsToEvaluate;
    }

}
