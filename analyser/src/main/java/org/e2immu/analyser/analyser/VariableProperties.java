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

import org.e2immu.analyser.util.SetOnceMap;

public class VariableProperties extends SetOnceMap<VariableProperty, Integer> {

    @Override
    public void put(VariableProperty variableProperty, Integer integer) {
        assert integer != null && integer >= 0; // not setting delays
        Integer inMap = super.getOtherwiseNull(variableProperty);
        if (inMap == null) {
            super.put(variableProperty, integer);
        } else if (!inMap.equals(integer)) {
            throw new IllegalArgumentException("Changing value of " + variableProperty + " from " + inMap + " to " + integer);
        }
    }

}
