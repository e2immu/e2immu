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

package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.model.Element;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.DependentVariable;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class ExtractVariablesToBeTranslated implements Predicate<Element> {
    final Set<Expression> expressions = new HashSet<>();
    final AtomicBoolean containsVariableFields = new AtomicBoolean();
    final AtomicReference<CausesOfDelay> causes = new AtomicReference<>(CausesOfDelay.EMPTY);
    final Predicate<FieldReference> isVariableField;
    final InspectionProvider inspectionProvider;
    final Set<Variable> variablesToIgnore = new HashSet<>();
    final boolean includeReturnValue;
    final boolean addDelayedVariableExpressions;

    public ExtractVariablesToBeTranslated(Predicate<FieldReference> isVariableField,
                                          InspectionProvider inspectionProvider,
                                          boolean includeReturnValue,
                                          boolean addDelayedVariableExpressions) {
        this.isVariableField = isVariableField;
        this.inspectionProvider = inspectionProvider;
        this.includeReturnValue = includeReturnValue;
        this.addDelayedVariableExpressions = addDelayedVariableExpressions;
    }

    @Override
    public boolean test(Element e) {
        if (e instanceof Lambda lambda) {
            variablesToIgnore.addAll(lambda.methodInfo.methodInspection.get().getParameters());
            variablesToIgnore.add(new This(inspectionProvider, lambda.methodInfo.typeInfo));
            return true;
        }
        if (e instanceof InlinedMethod im) {
            variablesToIgnore.addAll(im.getMyParameters());
            variablesToIgnore.add(new This(inspectionProvider, im.getMethodInfo().typeInfo));
            return true;
        }
        if (e instanceof VariableExpression ve) {
            add(ve.variable(), ve);
            return false;
        }
        if (e instanceof DelayedVariableExpression dve) {
            causes.set(causes.get().merge(dve.causesOfDelay));
            if (addDelayedVariableExpressions) {
                add(dve.variable, dve);
            }
            return false;
        }
        if (includeReturnValue && e instanceof UnknownExpression uk && uk.isReturnValue()) {
            expressions.add(uk);
            return false;
        }
        return true;
    }

    private void add(Variable v, IsVariableExpression variableExpression) {
        // essentially, parameters and this from lambda's inside the method; they stay as they are
        if (variablesToIgnore.contains(v)) return;

        // all the rest is added, work will be done during expansion
        expressions.add(variableExpression);

        if (v instanceof FieldReference fr) {
            boolean contains = isVariableField.test(fr);
            if (contains) containsVariableFields.set(true);
            if (fr.scope instanceof VariableExpression ve) {
                add(ve.variable(), ve);
            } else if (addDelayedVariableExpressions && fr.scope instanceof DelayedVariableExpression dve) {
                // FIXME is DVE allowed as scope?
                add(dve.variable, dve);
            }
        }
        if (v instanceof DependentVariable dv) {
            if (dv.arrayExpression() instanceof VariableExpression ve) {
                add(ve.variable(), ve);
            }
            if (addDelayedVariableExpressions && dv.arrayExpression() instanceof DelayedVariableExpression dve) {
                add(dve.variable, dve);
            }
            if (dv.indexExpression() instanceof VariableExpression ve) {
                add(ve.variable(), ve);
            }
            if (addDelayedVariableExpressions && dv.indexExpression() instanceof DelayedVariableExpression dve) {
                add(dve.variable, dve);
            }
        }
        // IMPORTANT: when adding a VE with a field reference, we do not rely on visit recursion anymore,
        // but on the recursion with add(...) why? USED TO BE: because that one also works once within an ExpandedVariable.
        // now: no real reason anymore, maybe rewrite when this stays stable.
    }

    public boolean isContainsVariableFields() {
        return containsVariableFields.get();
    }

    public CausesOfDelay getCauses() {
        return causes.get();
    }

    public Set<Expression> getExpressions() {
        return expressions;
    }
}
