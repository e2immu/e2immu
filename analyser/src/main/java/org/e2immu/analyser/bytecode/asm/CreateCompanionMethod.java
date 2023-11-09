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

package org.e2immu.analyser.bytecode.asm;

import org.e2immu.analyser.annotationxml.model.MethodItem;
import org.e2immu.analyser.inspector.impl.MethodInspectionImpl;
import org.e2immu.analyser.model.CompanionMethodName;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MethodInspection;
import org.e2immu.analyser.model.TypeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
in the method item, we have
- the name including the parameter types
- separately, the parameter names, as a csv
- and the function content is a string

Depending on the companion type, the first group of parameter types should agree with those
currently in the method inspection builder.
The companion method is not necessarily static. (It is only when this. is used...)
*/

public class CreateCompanionMethod {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateCompanionMethod.class);

    public static void add(TypeInfo currentType,
                           MethodInspectionImpl.Builder methodInspectionBuilder,
                           MethodItem companionMethod) {

        String name = companionMethod.name.substring(0, companionMethod.name.indexOf('('));
        CompanionMethodName companionMethodName = CompanionMethodName.extract(name);
        LOGGER.debug("Extracted {}", companionMethodName);

        MethodInspection.Builder companionBuilder = new MethodInspectionImpl.Builder(currentType, name,
                MethodInfo.MethodType.STATIC_METHOD);
        // FIXME more code; ticket https://github.com/e2immu/e2immu/issues/39
    }
}
