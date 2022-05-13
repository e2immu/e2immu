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

package org.e2immu.analyser.analyser;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestProperty {

    Set<Property> CONTEXT = Set.of(Property.CONTEXT_IMMUTABLE, Property.CONTEXT_CONTAINER,
            Property.CONTEXT_MODIFIED, Property.CONTEXT_NOT_NULL);

    Set<Property> EXTERNAL = Set.of(Property.EXTERNAL_IMMUTABLE, Property.EXTERNAL_CONTAINER,
            Property.EXTERNAL_NOT_NULL, Property.EXTERNAL_IGNORE_MODIFICATIONS);

    @Test
    public void testContext() {
        for (Property property : Property.values()) {
            assertEquals(property.propertyType == Property.PropertyType.CONTEXT, CONTEXT.contains(property),
                    "for " + property);
        }
    }

    @Test
    public void testExternal() {
        for (Property property : Property.values()) {
            assertEquals(property.propertyType == Property.PropertyType.EXTERNAL, EXTERNAL.contains(property),
                    "for " + property);
        }
    }

    @Test
    public void testValue() {
        for (Property property : Property.values()) {
            assertEquals(property.propertyType== Property.PropertyType.VALUE,
                    EvaluationContext.VALUE_PROPERTIES.contains(property), "for " + property);
        }
    }
}
