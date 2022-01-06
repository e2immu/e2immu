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
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.VariableInfo;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;

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

    Origin getOrigin();

    Expression getValue();

    DV getProperty(Property property);

    LinkedVariables getLinkedVariables();

    Comparator<ValueAndPropertyProxy> COMPARATOR =
            (p1, p2) -> ExpressionComparator.SINGLETON.compare(p1.getValue(), p2.getValue());

    record ValueAndPropertyProxyBasedOnVariableInfo(VariableInfo variableInfo,
                                                    Origin origin) implements ValueAndPropertyProxy {

        @Override
        public Origin getOrigin() {
            return origin;
        }

        @Override
        public Expression getValue() {
            return variableInfo.getValue();
        }

        @Override
        public DV getProperty(Property property) {
            return variableInfo.getProperty(property);
        }

        @Override
        public LinkedVariables getLinkedVariables() {
            return variableInfo.getLinkedVariables();
        }

        @Override
        public String toString() {
            return origin + ":" + getValue();
        }
    }
}