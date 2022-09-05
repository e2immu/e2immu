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

package org.e2immu.analyser.visitor;

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.AnalysisStatus;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.TypeInspection;
import org.e2immu.analyser.parser.Primitives;

import java.util.Map;

public interface TypeAnalyserVisitor {
    void visit(Data data);

    record Data(int iteration,
                boolean allowBreakDelay,
                Primitives primitives,
                TypeInfo typeInfo,
                TypeInspection typeInspection,
                TypeAnalysis typeAnalysis,
                Map<String, AnalysisStatus> statuses,
                AnalysisProvider analysisProvider) implements CommonVisitorData {
        @Override
        public DV getProperty(Property property) {
            return typeAnalysis.getProperty(property);
        }

        @Override
        public String label() {
            return typeInfo.fullyQualifiedName;
        }
    }
}
