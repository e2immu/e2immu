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

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.annotation.NotNull;

import java.util.Comparator;

public interface ValueAndPropertyProxy {

    enum Origin {
        // Field field = someValue;
        INITIALISER,
        // Field field; // implicitly, == null;
        EMPTY_INITIALISER,
        // ... this.field = field in constructor
        CONSTRUCTION,
        // { staticField = 3; } in static block
        STATIC_BLOCK,
        // this.field = field in setter, publicly accessible
        METHOD
    }

    @NotNull
    Origin getOrigin();

    @NotNull
    Expression getValue();

    DV getProperty(Property property);

    DV getPropertyOrDefaultNull(Property property);

    @NotNull
    LinkedVariables getLinkedVariables();

    Comparator<ValueAndPropertyProxy> COMPARATOR =
            (p1, p2) -> ExpressionComparator.SINGLETON.compare(p1.getValue(), p2.getValue());

    record ProxyData(Expression value,
                     Properties properties,
                     LinkedVariables linkedVariables,
                     Origin origin) implements ValueAndPropertyProxy {

        @Override
        public Origin getOrigin() {
            return origin;
        }

        @Override
        public Expression getValue() {
            return value;
        }

        @Override
        public DV getProperty(Property property) {
            return properties.get(property);
        }

        @Override
        public DV getPropertyOrDefaultNull(Property property) {
            return properties.getOrDefaultNull(property);
        }

        @Override
        public LinkedVariables getLinkedVariables() {
            return linkedVariables;
        }

        @Override
        public String toString() {
            return origin + ":" + getValue();
        }
    }

    default boolean isLinkedToParameter(DV requiredLevel) {
        DV acceptLv = requiredLevel.le(MultiLevel.EFFECTIVELY_NOT_NULL_DV) ? LinkedVariables.LINK_ASSIGNED : LinkedVariables.LINK_INDEPENDENT1;
        return getLinkedVariables().variables().entrySet().stream().anyMatch(e ->
                e.getKey() instanceof ParameterInfo && e.getValue().ge(LinkedVariables.LINK_STATICALLY_ASSIGNED) && e.getValue().le(acceptLv));
    }
}
