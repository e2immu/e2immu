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

package org.e2immu.analyser.parser.start.testexample;


import java.util.*;

public class SubTypes_12 {

    interface DV {
        boolean isDelayed();
    }

    record DVImpl(String s) implements DV {
        public DVImpl {
            assert s != null;
        }

        @Override
        public boolean isDelayed() {
            return s.length() == 1;
        }
    }

    enum Stage {EVALUATION}

    interface VariableInfo {
        Map<String, String> getProperties();

        DV getProperty(String s);

        DV getValue();

        void setValue(DV dv);

        List<String> getLinkedVariables();
    }

    record VariableInfoImpl(Map<String, String> map) implements VariableInfo {

        public VariableInfoImpl {
            Objects.requireNonNull(map);
        }

        void setProperty(String k, String v) {
            map.put(k, v);
        }

        @Override
        public Map<String, String> getProperties() {
            return map;
        }

        @Override
        public DV getProperty(String s) {
            return map.get(s) == null ? null : new DVImpl(s);
        }

        @Override
        public DV getValue() {
            return null;
        }

        @Override
        public void setValue(DV dv) {

        }

        @Override
        public List<String> getLinkedVariables() {
            return map.values().stream().toList();
        }

        void setLinkedVariables(List<String> list) {
            list.forEach(s -> map.put(s, s));
        }
    }

    private static VariableInfo best(Stage stage) {
        return new VariableInfoImpl(new HashMap<>());
    }

    public static void method(Optional<VariableInfoImpl> merge) {
        if (haveMerge(merge)) {
            VariableInfo best = best(Stage.EVALUATION);
            VariableInfoImpl mergeImpl = merge.get();
            if (mergeImpl.getValue().isDelayed()) mergeImpl.setValue(best.getValue());
            mergeImpl.setLinkedVariables(best.getLinkedVariables());
            best.getProperties().forEach((k, v) -> {
                DV dv = mergeImpl.getProperty(k);
                if (dv == null || dv.isDelayed()) mergeImpl.setProperty(k, v);
            });
        }
    }

    private static boolean haveMerge(Optional<VariableInfoImpl> merge) {
        return merge.isPresent() && merge.get().map.containsKey("abc");
    }
}
