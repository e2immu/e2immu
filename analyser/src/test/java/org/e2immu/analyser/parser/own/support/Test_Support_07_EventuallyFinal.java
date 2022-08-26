
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

package org.e2immu.analyser.parser.own.support;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.CommonTestRunner;
import org.e2immu.analyser.visitor.FieldAnalyserVisitor;
import org.e2immu.analyser.visitor.TypeAnalyserVisitor;
import org.e2immu.support.EventuallyFinal;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Test_Support_07_EventuallyFinal extends CommonTestRunner {

    // cannot be set to true because there is a OrgE2ImmuSupport.java A API file which refers to this type.
    // we currently cannot have both at the same time
    public Test_Support_07_EventuallyFinal() {
        super(false);
    }

    @Test
    public void test() throws IOException {
        FieldAnalyserVisitor fieldAnalyserVisitor = d -> {
            if ("value".equals(d.fieldInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.FINAL);
            }
            if ("isFinal".equals(d.fieldInfo().name)) {
                assertDv(d, DV.FALSE_DV, Property.FINAL);
            }
        };

        TypeAnalyserVisitor typeAnalyserVisitor = d -> {
            if ("EventuallyFinal".equals(d.typeInfo().simpleName)) {
                if (d.iteration() >= 1) {
                    Map<FieldReference, Expression> map = d.typeAnalysis().getApprovedPreconditionsFinalFields();
                    assertEquals("isFinal=!isFinal",
                            map.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString()))
                                    .map(Object::toString).collect(Collectors.joining(",")));
                    Map<FieldReference, Expression> map2 = d.typeAnalysis().getApprovedPreconditionsImmutable();
                    assertEquals("isFinal=!isFinal",
                            map2.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().toString()))
                                    .map(Object::toString).collect(Collectors.joining(",")));
                }
                assertEquals("Type param T", d.typeAnalysis().getHiddenContentTypes().toString());
                assertDv(d, 2, MultiLevel.EVENTUALLY_IMMUTABLE_HC_DV, Property.IMMUTABLE);
            }
        };

        testSupportAndUtilClasses(List.of(EventuallyFinal.class), 0, 0, new DebugConfiguration.Builder()
                .addAfterTypeAnalyserVisitor(typeAnalyserVisitor)
                .addAfterFieldAnalyserVisitor(fieldAnalyserVisitor)
                .build());
    }

}
