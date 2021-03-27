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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.InspectionProvider;

import java.util.Set;

import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

public class MethodCallIncompatibleWithPrecondition {
    /*
    look into the latest value of the fields
    they must have instance state that is incompatible with the precondition.

    return null upon delays.
     */
    public static Boolean isMark(EvaluationContext evaluationContext,
                                 Precondition precondition,
                                 Set<FieldInfo> fields,
                                 MethodAnalyser methodAnalyser) {
        assert !precondition.isEmpty();

        StatementAnalysis statementAnalysis = methodAnalyser.methodAnalysis.getLastStatement();
        for (FieldInfo fieldInfo : fields) {
            FieldReference fieldReference = new FieldReference(InspectionProvider.DEFAULT,
                    fieldInfo, new This(InspectionProvider.DEFAULT, methodAnalyser.methodInfo.typeInfo));
            VariableInfo variableInfo = statementAnalysis.findOrNull(fieldReference, VariableInfoContainer.Level.MERGE);
            if (!variableInfo.valueIsSet()) {
                log(DELAYED, "Delaying isMark, no value for field {} in last statement of {}",
                        fieldInfo.name, methodAnalyser.methodInfo.fullyQualifiedName);
                return null;
            }
            if (variableInfo.getValue() instanceof NewObject newObject) {
                Expression state = newObject.state();
                if (!state.isBoolValueTrue()) {
                    Expression and = new And(evaluationContext.getPrimitives()).append(evaluationContext,
                            precondition.expression(), state);
                    if (and.isBoolValueFalse()) return true;
                }
            }
        }
        return false;
    }
}
