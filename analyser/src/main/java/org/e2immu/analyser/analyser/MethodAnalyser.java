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

import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MethodInspection;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public interface MethodAnalyser extends Analyser {
    @NotNull1
    Stream<PrimaryTypeAnalyser> getLocallyCreatedPrimaryTypeAnalysers();

    @NotNull1
    Stream<VariableInfo> getFieldAsVariableStream(FieldInfo fieldInfo);

    @NotNull
    StatementAnalyser findStatementAnalyser(String index);

    void logAnalysisStatuses();

    @NotNull1
    Collection<? extends ParameterAnalyser> getParameterAnalysers();

    boolean hasCode();

    CausesOfDelay fromFieldToParametersStatus();

    @NotNull
    MethodAnalysis getMethodAnalysis();

    @NotNull
    MethodInfo getMethodInfo();

    boolean isSAM();

    @NotNull
    MethodInspection getMethodInspection();

    @NotNull1
    List<ParameterAnalysis> getParameterAnalyses();
}
