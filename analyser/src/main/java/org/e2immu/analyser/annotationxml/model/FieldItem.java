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

import org.e2immu.analyser.model.FieldInfo;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FieldItem extends HasAnnotations implements Comparable<FieldItem> {
    public final String name;

    public FieldItem(String name) {
        this.name = name;
    }

    public FieldItem(FieldInfo fieldInfo) {
        this.name = fieldInfo.name;
        addAnnotations(fieldInfo.fieldInspection.isSet() ? fieldInfo.fieldInspection.get().getAnnotations() : List.of(),
                fieldInfo.fieldAnalysis.isSet() ?
                        fieldInfo.fieldAnalysis.get().getAnnotationStream().filter(e -> e.getValue().isPresent())
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList()) : List.of());
        freeze();
    }

    @Override
    public int compareTo(FieldItem o) {
        return name.compareTo(o.name);
    }
}
