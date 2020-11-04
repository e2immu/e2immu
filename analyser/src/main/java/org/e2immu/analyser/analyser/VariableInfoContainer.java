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

import org.e2immu.analyser.util.Freezable;

/**
 * Container to store different versions of a VariableInfo object, one or more of this list:
 * <ol>
 *     <li>0 - reference to previous: simply a reference to a previous version, NEVER written</li>
 *     <li>1 - initialisation phase: assignments like <code>int i=3;</code> or <code>for(i=0; ...)</code></li>
 *     <li>2 - evaluation phase: typically does not contain an assignment, but is possible is in <code>while((line = reader.next()) != null)</code></li>
 *     <li>3 - update phase: only in <code>for(...; i++)</code> constructs</li>
 *     <li>4 - summary phase: information from sub-blocks</li>
 * </ol>
 * <p>
 * Only in very rare situations, all 5 can be present.
 * Generally, outside of the statement analyser, the highest version will be inspected.
 * <p>
 * Can only be created in increasing levels! Will be frozen as soon as the statement analyser goes AnalysisStatus == DONE.
 */
public class VariableInfoContainer extends Freezable {
    public static final int LEVELS = 5;
    public static final int LEVEL_0_PREVIOUS = 0;
    public static final int LEVEL_1_INITIALISER = 1;
    public static final int LEVEL_2_EVALUATION = 2;
    public static final int LEVEL_3_UPDATER = 3;
    public static final int LEVEL_4_SUMMARY = 4;

    private final VariableInfo[] data = new VariableInfo[LEVELS];
    private int highestLevel = -1;

    public VariableInfoContainer(VariableInfo previous) {
        if (previous != null) {
            set(LEVEL_0_PREVIOUS, previous);
        }
    }

    public boolean haveData() {
        return highestLevel >= 0;
    }

    public int getHighestLevel() {
        return highestLevel;
    }

    public VariableInfo current() {
        if (highestLevel == -1) throw new UnsupportedOperationException("No VariableInfo object set yet");
        return data[highestLevel];
    }

    public VariableInfo get(int level) {
        return data[level];
    }

    public void set(int level, VariableInfo variableInfo) {
        if (level <= highestLevel) throw new UnsupportedOperationException();
        data[level] = variableInfo;
        highestLevel = level;
    }
}
