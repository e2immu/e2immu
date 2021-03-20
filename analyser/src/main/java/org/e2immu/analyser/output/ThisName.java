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

package org.e2immu.analyser.output;

import org.e2immu.analyser.util.StringUtil;

public record ThisName(boolean isSuper, Qualifier qualifier, boolean qualifierRequired) implements Qualifier {

    @Override
    public String minimal() {
        String thisOrSuper = isSuper ? "super" : "this";
        return qualifierRequired ? qualifier.minimal() + "." + thisOrSuper : thisOrSuper;
    }

    @Override
    public String write(FormattingOptions options) {
        String thisOrSuper = isSuper ? "super" : "this";
        return qualifierRequired ? qualifier.write(options) + "." + thisOrSuper : thisOrSuper;
    }

    @Override
    public String generateJavaForDebugging() {
        String q = qualifier == null ? "null" : StringUtil.quote(qualifier.minimal());
        return ".add(new ThisName(" + isSuper + ", " + q + ", " + qualifierRequired + "))";
    }
}
