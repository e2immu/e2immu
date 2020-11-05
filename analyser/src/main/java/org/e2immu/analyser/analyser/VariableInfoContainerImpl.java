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

import org.e2immu.analyser.model.Value;
import org.e2immu.analyser.model.Variable;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.Freezable;

import java.util.Objects;
import java.util.Set;

public class VariableInfoContainerImpl extends Freezable implements VariableInfoContainer {
    public static final int LEVELS = 5;

    private final VariableInfo[] data = new VariableInfo[LEVELS];
    private int currentLevel;

    public VariableInfoContainerImpl(VariableInfo previous) {
        Objects.requireNonNull(previous);
        data[LEVEL_0_PREVIOUS] = previous;
        currentLevel = LEVEL_0_PREVIOUS;
    }

    public VariableInfoContainerImpl(Variable variable, String name) {
        Objects.requireNonNull(variable);
        Objects.requireNonNull(name);
        data[LEVEL_1_INITIALISER] = new VariableInfoImpl(variable, name);
        currentLevel = LEVEL_1_INITIALISER;
    }

    @Override
    public VariableInfo current() {
        if (currentLevel == -1) throw new UnsupportedOperationException("No VariableInfo object set yet");
        return data[currentLevel];
    }

    public VariableInfo get(int level) {
        return data[level];
    }

    @Override
    public int getCurrentLevel() {
        return currentLevel;
    }

    @Override
    public void freeze() {
        for (int i = 0; i < data.length; i++) {
            if (data[i] != null) {
                data[i] = data[i].freeze();
            }
        }
        super.freeze();
    }

    /* ******************************* modifying methods related to assignment ************************************** */

    @Override
    public void assignment(int level) {
        ensureNotFrozen();
        if (level <= currentLevel) {
            if (data[level] != null) {
                // the data is already there
                return;
            }
            throw new UnsupportedOperationException("In the first iteration, an assignment should start a new level");
        }
        int assigned = data[currentLevel].getProperty(VariableProperty.ASSIGNED);
        currentLevel = level;
        VariableInfoImpl variableInfo = new VariableInfoImpl(data[0].variable(), data[0].name());
        data[currentLevel] = variableInfo;
        variableInfo.setProperty(VariableProperty.ASSIGNED, assigned);
    }

    private VariableInfoImpl currentLevelForWriting(int level) {
        if (level <= 0 || level >= LEVELS) throw new IllegalArgumentException();
        VariableInfoImpl variableInfo = (VariableInfoImpl) data[currentLevel];
        if (variableInfo == null) {
            throw new IllegalArgumentException("No assignment announcement was made at level " + level);
        }
        return variableInfo;
    }

    @Override
    public void setValue(int level, Value value) {
        ensureNotFrozen();

        Objects.requireNonNull(value);
        if (value != UnknownValue.NO_VALUE) {
            throw new IllegalArgumentException("Value should not be NO_VALUE");
        }
        VariableInfoImpl variableInfo = currentLevelForWriting(level);
        variableInfo.value.set(value);
    }

    @Override
    public void setStateOnAssignment(int level, Value state) {
        ensureNotFrozen();
        Objects.requireNonNull(state);
        if (state != UnknownValue.NO_VALUE) {
            throw new IllegalArgumentException("State should not be NO_VALUE");
        }
        VariableInfoImpl variableInfo = currentLevelForWriting(level);
        variableInfo.stateOnAssignment.set(state);
    }

    @Override
    public void markAssigned(int level) {
        ensureNotFrozen();

        VariableInfoImpl variableInfo = currentLevelForWriting(level);
        // possible previous value was copied in assignment()
        int assigned = variableInfo.getProperty(VariableProperty.ASSIGNED);
        variableInfo.setProperty(VariableProperty.ASSIGNED, Math.max(1, assigned + 1));
    }

    /* ******************************* modifying methods unrelated to assignment ************************************ */

    private VariableInfoImpl findForWriting(int level) {
        if (level <= 0 || level >= LEVELS) throw new IllegalArgumentException();

        int levelToWrite = level;
        VariableInfoImpl variableInfo = null;
        while (levelToWrite >= 0 && (variableInfo = (VariableInfoImpl) data[levelToWrite]) == null) {
            levelToWrite--;
        }
        if (levelToWrite < 0) throw new UnsupportedOperationException("Should not be possible");
        if (levelToWrite == 0) {
            // need to make a new copy at given level, copying from 0
            assert data[0] != null;
            variableInfo = new VariableInfoImpl((VariableInfoImpl) data[0]);
            levelToWrite = level;
            data[levelToWrite] = variableInfo;
        }
        return variableInfo;
    }

    @Override
    public void setProperty(int level, VariableProperty variableProperty, int value) {
        ensureNotFrozen();
        Objects.requireNonNull(variableProperty);

        VariableInfoImpl variableInfo = findForWriting(level);
        variableInfo.setProperty(variableProperty, value);
    }

    @Override
    public void markRead(int level) {
        ensureNotFrozen();
        VariableInfoImpl variableInfo = findForWriting(level);
        // this could be the current level, but can be lower as well; because we're doing this only in the 1st iteration
        // there should be no problems with overwriting
        int assigned = Math.max(variableInfo.getProperty(VariableProperty.ASSIGNED), 0);
        int read = variableInfo.getProperty(VariableProperty.READ);
        int val = Math.max(1, Math.max(read + 1, assigned + 1));
        variableInfo.setProperty(VariableProperty.READ, val);
    }

    @Override
    public void setLinkedVariables(int level, Set<Variable> variables) {
        ensureNotFrozen();
        Objects.requireNonNull(variables);
        VariableInfoImpl variableInfo = findForWriting(level);
        variableInfo.linkedVariables.set(variables);
    }

    @Override
    public void setObjectFlow(int level, ObjectFlow objectFlow) {
        ensureNotFrozen();
        Objects.requireNonNull(objectFlow);
        VariableInfoImpl variableInfo = findForWriting(level);
        variableInfo.objectFlow.set(objectFlow);
    }
}
