/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;

public class ErrorFlags {

    public final SetOnce<Boolean> errorValue = new SetOnce<>(); // if we detected an error value on this statement

    // accumulating
    public final SetOnceMap<ParameterInfo, Boolean> parameterAssignments = new SetOnceMap<>();
    public final SetOnceMap<LocalVariable, Boolean> unusedLocalVariables = new SetOnceMap<>();
    public final SetOnceMap<Variable, Boolean> uselessAssignments = new SetOnceMap<>();
    public final SetOnceMap<FieldInfo, Boolean> errorAssigningToFieldOutsideType = new SetOnceMap<>();
    public final SetOnceMap<MethodInfo, Boolean> errorCallingModifyingMethodOutsideType = new SetOnceMap<>();

    public AnalysisStatus copy(StatementAnalysis statementAnalysis, StatementAnalysis previousStatementAnalysis) {
        if (previousStatementAnalysis != null) copy(previousStatementAnalysis.errorFlags, true);
        statementAnalysis.navigationData.blocks.get().forEach(sub -> copy(sub.errorFlags, false));
        return AnalysisStatus.DONE;
    }

    private void copy(ErrorFlags other, boolean complainWhenAlreadySet) {
        parameterAssignments.putAll(other.parameterAssignments, complainWhenAlreadySet);
        unusedLocalVariables.putAll(other.unusedLocalVariables, complainWhenAlreadySet);
        uselessAssignments.putAll(other.uselessAssignments, complainWhenAlreadySet);
        errorAssigningToFieldOutsideType.putAll(other.errorAssigningToFieldOutsideType, complainWhenAlreadySet);
        errorCallingModifyingMethodOutsideType.putAll(errorCallingModifyingMethodOutsideType, complainWhenAlreadySet);
    }
}
