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
    public final Messages messages = new Messages();

    public final SetOnce<Boolean> errorValue = new SetOnce<>(); // if we detected an error value on this statement

    // accumulating
    public final SetOnceMap<ParameterInfo, Boolean> parameterAssignments = new SetOnceMap<>();
    public final SetOnceMap<LocalVariable, Boolean> unusedLocalVariables = new SetOnceMap<>();
    public final SetOnceMap<Variable, Boolean> uselessAssignments = new SetOnceMap<>();
    public final SetOnceMap<FieldInfo, Boolean> errorAssigningToFieldOutsideType = new SetOnceMap<>();
    public final SetOnceMap<MethodInfo, Boolean> errorCallingModifyingMethodOutsideType = new SetOnceMap<>();

    public void update(ErrorFlags parent) {
        parameterAssignments.putAll(parent.parameterAssignments);
        unusedLocalVariables.putAll(parent.unusedLocalVariables);
        uselessAssignments.putAll(parent.uselessAssignments);
        errorAssigningToFieldOutsideType.putAll(parent.errorAssigningToFieldOutsideType);
        errorCallingModifyingMethodOutsideType.putAll(errorCallingModifyingMethodOutsideType);
        messages.addAll(parent.messages);
    }

    public void lift(ErrorFlags lastStatementSubBlock) {
        parameterAssignments.putAll(lastStatementSubBlock.parameterAssignments, false);
        unusedLocalVariables.putAll(lastStatementSubBlock.unusedLocalVariables, false);
        uselessAssignments.putAll(lastStatementSubBlock.uselessAssignments, false);
        errorAssigningToFieldOutsideType.putAll(lastStatementSubBlock.errorAssigningToFieldOutsideType, false);
        errorCallingModifyingMethodOutsideType.putAll(errorCallingModifyingMethodOutsideType, false);
        messages.addAll(lastStatementSubBlock.messages);
    }

    public class ErrorAssigningToFieldOutsideType implements StatementAnalysis.StatementAnalysisModification {
        private final FieldInfo fieldInfo;
        private final Location location;

        public ErrorAssigningToFieldOutsideType(FieldInfo fieldInfo, Location location) {
            this.fieldInfo = fieldInfo;
            this.location = location;
        }

        @Override
        public void run() {
            if (!errorAssigningToFieldOutsideType.isSet(fieldInfo)) {
                errorAssigningToFieldOutsideType.put(fieldInfo, true);
                messages.add(Message.newMessage(location, Message.ASSIGNMENT_TO_FIELD_OUTSIDE_TYPE));
            }
        }
    }

    public class ParameterShouldNotBeAssignedTo implements StatementAnalysis.StatementAnalysisModification {
        private final ParameterInfo parameterInfo;
        private final Location location;

        public ParameterShouldNotBeAssignedTo(ParameterInfo parameterInfo, Location location) {
            this.parameterInfo = parameterInfo;
            this.location = location;
        }

        @Override
        public void run() {
            if (!parameterAssignments.isSet(parameterInfo)) {
                parameterAssignments.put(parameterInfo, true);
                messages.add(Message.newMessage(location, Message.PARAMETER_SHOULD_NOT_BE_ASSIGNED_TO));
            }
        }
    }

    public class RaiseErrorMessage implements StatementAnalysis.StatementAnalysisModification {
        private final Message message;

        public RaiseErrorMessage(Message message) {
            this.message = message;
        }

        @Override
        public void run() {
            messages.add(message);
        }
    }
}
