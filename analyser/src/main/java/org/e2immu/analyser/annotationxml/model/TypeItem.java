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
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.annotation.E2Immutable;
import org.e2immu.annotation.Mark;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@E2Immutable(after = "freeze")
public class TypeItem extends HasAnnotations implements Comparable<TypeItem> {
    public final String name;

    //@Mark("freeze")
    public TypeItem(TypeInfo typeInfo) {
        this.name = typeInfo.fullyQualifiedName;
        boolean haveTypeInspection = typeInfo.typeInspection.isSet();
        addAnnotations(haveTypeInspection ? typeInfo.typeInspection.get().getAnnotations() : List.of(),
                typeInfo.typeAnalysis.isSet() ?
                        typeInfo.typeAnalysis.get().getAnnotationStream().filter(e -> e.getValue().isPresent())
                                .map(Map.Entry::getKey)
                                .collect(Collectors.toList()) : List.of());
        if (haveTypeInspection) {
            for (MethodInfo methodInfo : typeInfo.typeInspection.get().methods(TypeInspection.Methods.THIS_TYPE_ONLY_EXCLUDE_FIELD_SAM)) {
                MethodItem methodItem = new MethodItem(methodInfo, null);
                methodItems.put(methodItem.name, methodItem);
            }
            for (FieldInfo fieldInfo : typeInfo.typeInspection.get().fields()) {
                FieldItem fieldItem = new FieldItem(fieldInfo);
                fieldItems.put(fieldItem.name, fieldItem);
            }
        }
        freeze();
    }

    public TypeItem(String name) {
        this.name = name;
    }

    private Map<String, MethodItem> methodItems = new HashMap<>();
    private Map<String, FieldItem> fieldItems = new HashMap<>();

    @Mark("freeze")
    public void freeze() {
        super.freeze();
        methodItems = Map.copyOf(methodItems);
        methodItems.values().forEach(MethodItem::freeze);
        fieldItems = Map.copyOf(fieldItems);
        fieldItems.values().forEach(FieldItem::freeze);
    }

    public Map<String, MethodItem> getMethodItems() {
        return methodItems;
    }

    public Map<String, FieldItem> getFieldItems() {
        return fieldItems;
    }

    @Override
    public int compareTo(TypeItem o) {
        return name.compareTo(o.name);
    }
}
