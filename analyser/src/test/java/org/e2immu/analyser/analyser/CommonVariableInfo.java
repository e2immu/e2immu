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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.BooleanConstant;
import org.e2immu.analyser.model.expression.IntConstant;
import org.e2immu.analyser.model.expression.NullConstant;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.impl.PrimitivesImpl;

public abstract class CommonVariableInfo {

    protected final Primitives primitives = new PrimitivesImpl();

    protected Variable makeLocalIntVar(String name) {
        return new LocalVariableReference(
                // owningType is completely arbitrary
                new LocalVariable.Builder()
                        .setName(name)
                        .setParameterizedType(primitives.intParameterizedType())
                        .setOwningType(primitives.stringTypeInfo())
                        .build());
    }

    protected Variable makeLocalBooleanVar() {
        return new LocalVariableReference(
                new LocalVariable.Builder()
                        .setName("x")
                        .setSimpleName("x")
                        .setParameterizedType(primitives.booleanParameterizedType())
                        .setOwningType(primitives.stringTypeInfo())
                        .build());
    }

    protected Variable makeReturnVariable() {
        return new ReturnVariable(primitives.orOperatorBool());
    }

    protected final IntConstant two = new IntConstant(primitives, 2);
    protected final IntConstant three = new IntConstant(primitives, 3);
    protected final IntConstant four = new IntConstant(primitives, 4);

    protected final BooleanConstant TRUE = new BooleanConstant(primitives, true);

    protected final AnalyserContext analyserContext = () -> primitives;

    protected final EvaluationContext minimalEvaluationContext = new EvaluationContext() {

        @Override
        public TypeInfo getCurrentType() {
            return primitives.stringTypeInfo();
        }

        @Override
        public Location getLocation() {
            return Location.NOT_YET_SET;
        }

        @Override
        public AnalyserContext getAnalyserContext() {
            return analyserContext;
        }

        @Override
        public Expression currentValue(Variable variable, int statementTime, ForwardEvaluationInfo forwardEvaluationInfo) {
            return new VariableExpression(variable);
        }

        @Override
        public boolean isNotNull0(Expression value, boolean useEnnInsteadOfCnn) {
            return !(value instanceof NullConstant); // no opinion
        }

        @Override
        public Primitives getPrimitives() {
            return primitives;
        }

        @Override
        public ConditionManager getConditionManager() {
            return ConditionManager.initialConditionManager(primitives);
        }
    };

}
