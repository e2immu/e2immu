/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Analysis;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.parser.Message;

import java.util.stream.Stream;

public interface Analyser {
    Stream<Message> getMessageStream();

    void check();

    WithInspectionAndAnalysis getMember();

    AnalysisStatus analyse(int iteration);

    void initialize();

    Analysis getAnalysis();

    String getName();

    AnalyserComponents<String> getAnalyserComponents();

}
