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

package org.e2immu.analyser.annotationxml.model;

import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.annotation.E2Immutable;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ParameterItem extends HasAnnotations implements Comparable<ParameterItem> {
    public final int index;

    public ParameterItem(int parameterIndex) {
        index = parameterIndex;
    }

    public ParameterItem(ParameterInfo parameterInfo) {
        index = parameterInfo.index;
        addAnnotations(parameterInfo.parameterInspection.isSet() ? parameterInfo.parameterInspection.get().getAnnotations() : List.of(),
                parameterInfo.parameterAnalysis.isSet() ?
                        parameterInfo.parameterAnalysis.get().getAnnotationStream().filter(e -> e.getValue().isPresent())
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList()) : List.of());
        freeze();
    }

    @Override
    public int compareTo(ParameterItem o) {
        return index - o.index;
    }
}
