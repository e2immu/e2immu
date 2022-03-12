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

package org.e2immu.analyser.parser.minor.testexample;


public class InstanceOf_15 {

    interface Variable {
    }

    record This() implements Variable {
    }

    interface EvaluationContext {
    }

    interface Expression {
    }

    interface DV {
        DV NULLABLE_DV = new DV() {
            @Override
            public boolean isDelayed() {
                return false;
            }

            @Override
            public int hashCode() {
                return super.hashCode();
            }
        };

        boolean isDelayed();
    }

    static class Builder {
        EvaluationContext evaluationContext;

        public void variableOccursInNotNullContext(Variable variable, Expression value, DV notNullRequired) {
            assert evaluationContext != null;
            assert value != null;
            if (notNullRequired.equals(DV.NULLABLE_DV)) return;
            if (notNullRequired.isDelayed()) {
                // simply set the delay
                setProperty(variable, notNullRequired);
                return;
            }
            if (variable instanceof This) return; // nothing to be done here
            setProperty(variable, notNullRequired);
        }

        void setProperty(Variable variable, DV dv) {
            variable.toString(); // essential to the test!!!
        }
    }
}