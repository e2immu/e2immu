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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.AnnotationParameters;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.UpgradableBooleanMap;

import java.util.List;
import java.util.Set;


public interface AnnotationExpression {

    TypeInfo typeInfo();

    List<Expression> expressions();

    Set<String> imports();

    <T> T extract(String fieldName, T defaultValue);

    int[] extractIntArray(String parameters);

    AnnotationExpression copyWith(Primitives primitives, String parameter, String value);

    UpgradableBooleanMap<TypeInfo> typesReferenced();

    AnnotationParameters e2ImmuAnnotationParameters();

    OutputBuilder output(Qualification qualification);
}
