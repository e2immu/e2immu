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

package org.e2immu.analyser.analyser.delay;

import org.e2immu.analyser.analyser.CauseOfDelay;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.Location;
import org.e2immu.analyser.model.TranslationMap;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.InspectionProvider;

public class VariableCause implements CauseOfDelay {
    private final Variable variable;
    private final Location location;
    private final CauseOfDelay.Cause cause;
    private final String withoutStatementIdentifier;

    public VariableCause(Variable variable, Location location, Cause cause) {
        this.variable = variable;
        this.location = location;
        this.cause = cause;
        this.withoutStatementIdentifier = cause.label + ":" + variable.minimalOutput() + "@" + location.delayStringWithoutStatementIdentifier();
    }

    @Override
    public int compareTo(CauseOfDelay o) {
        if (o instanceof VariableCause vc) {
            int c = cause.compareTo(vc.cause);
            if (c != 0) return c;
            int d = variable.compareTo(vc.variable);
            if (d != 0) return d;
            return location.compareTo(vc.location);
        }
        return -1; // VC comes before SimpleCause
    }

    @Override
    public String toString() {
        return cause.label + ":" + variable.minimalOutput() + "@" + location.toDelayString();
    }

    @Override
    public Cause cause() {
        return cause;
    }

    @Override
    public String withoutStatementIdentifier() {
        return withoutStatementIdentifier;
    }

    @Override
    public Location location() {
        return location;
    }

    public Variable variable() {
        return variable;
    }

    @Override
    public boolean variableIsField(FieldInfo fieldInfo) {
        return variable instanceof FieldReference fr && fr.fieldInfo() == fieldInfo;
    }

    public CauseOfDelay translate(InspectionProvider inspectionProvider, TranslationMap translationMap) {
        Variable translated = translationMap.translateVariable(inspectionProvider, variable);
        if(translated != variable) return new VariableCause(translated, location, cause);
        return this;
    }
}
