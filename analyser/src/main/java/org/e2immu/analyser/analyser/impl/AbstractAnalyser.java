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

package org.e2immu.analyser.analyser.impl;

import org.e2immu.analyser.analyser.Analyser;
import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.PrimaryTypeAnalyser;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

abstract class AbstractAnalyser implements Analyser {
    public final AnalyserContext analyserContext;
    public final String name;

    protected AbstractAnalyser(String name, AnalyserContext analyserContext) {
        this.analyserContext = Objects.requireNonNull(analyserContext);
        this.name = name;
    }

    protected final AnalyserResult.Builder analyserResultBuilder = new AnalyserResult.Builder();

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }


    @Override
    public void receiveAdditionalTypeAnalysers(Collection<PrimaryTypeAnalyser> typeAnalysers) {
        // no-op
    }

    @Override
    public Stream<Message> getMessageStream() {
        return analyserResultBuilder.getMessageStream();
    }

    @Override
    public void makeImmutable() {
        // default do nothing
    }
}
