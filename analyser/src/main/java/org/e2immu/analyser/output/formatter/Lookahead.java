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

package org.e2immu.analyser.output.formatter;

import org.e2immu.analyser.output.FormattingOptions;
import org.e2immu.analyser.output.Guide;
import org.e2immu.analyser.output.OutputElement;
import org.e2immu.analyser.output.Space;

import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

public class Lookahead {

    /**
     * Tries to fit all the remaining output elements on a single line.
     * If that works, then the last element is returned.
     * If a newline is encountered, then the newline is returned.
     * If not everything fits on a line, and the guides allow it, we return a guide position,
     * which can be START, MID or END
     */

    public static CurrentExceeds lookAhead(FormattingOptions options, List<OutputElement> list, int start, int lineLength) {
        AtomicReference<ForwardInfo> currentForwardInfo = new AtomicReference<>();
        AtomicReference<ForwardInfo> prioritySplit = new AtomicReference<>();
        AtomicReference<ForwardInfo> exceeds = new AtomicReference<>();
        Stack<GuideOnStack> startOfGuides = new Stack<>();
        Forward.forward(options, list, forwardInfo -> {
            if (forwardInfo.charsPlusString() > lineLength) {

                // exceeding the allowed length of the line... not everything fits on one line
                if (prioritySplit.get() != null) {
                    // give priority to symmetrical splits
                    currentForwardInfo.set(prioritySplit.get());
                    return true;
                }
                if (!startOfGuides.isEmpty()) {
                    // then to the first of the guides
                    currentForwardInfo.set(startOfGuides.get(0).forwardInfo);
                    return true;
                }
                if (!forwardInfo.symbol() && !forwardInfo.isGuide()) {
                    // mark that we're going over the limit
                    exceeds.set(forwardInfo);
                    assert forwardInfo.guide() == null;
                    return true;
                }
            }
            currentForwardInfo.set(forwardInfo);

            if (forwardInfo.isGuide()) {
                Guide guide = forwardInfo.guide();
                switch (guide.position()) {
                    case START -> {
                        // priority split: when the first one we encounter is a priority one
                        if (prioritySplit.get() == null && guide.prioritySplit() && acceptPriority(startOfGuides)) {
                            prioritySplit.set(forwardInfo);
                        }
                        startOfGuides.push(new GuideOnStack(forwardInfo));
                    }
                    case MID -> {
                        // we started the lookahead in the middle of a guide; encountering another mid
                        // that's where we need to stop
                        if (startOfGuides.isEmpty()) {
                            return true; // stop
                        }
                        assert startOfGuides.peek().forwardInfo.guide().index() == guide.index();
                        startOfGuides.peek().increment();
                    }
                    case END -> {
                        if (startOfGuides.isEmpty()) {
                            return true; // stop
                        }
                        assert startOfGuides.peek().forwardInfo.guide().index() == guide.index();
                        startOfGuides.pop();
                    }
                }
            }
            OutputElement outputElement = list.get(forwardInfo.pos());
            return outputElement == Space.NEWLINE; // stop on newline, otherwise continue
        }, start, Math.max(100, lineLength * 3) / 2);
        // both can be null, when the forward method immediately hits a newline
        return new CurrentExceeds(currentForwardInfo.get(), exceeds.get());
    }

    /**
     * We accept priority splits when the only other splits have started at the beginning,
     * and no MIDs have been encountered for them
     */
    private static boolean acceptPriority(Stack<GuideOnStack> startOfGuides) {
        return startOfGuides.stream().allMatch(guideOnStack -> guideOnStack.forwardInfo.chars() == 0 &&
                guideOnStack.getCountMid() == 0);
    }
}
