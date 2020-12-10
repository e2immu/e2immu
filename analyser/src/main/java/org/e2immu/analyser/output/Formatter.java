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

package org.e2immu.analyser.output;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public record Formatter(FormattingOptions options) {

    public String write(OutputBuilder outputBuilder) {
        try (StringWriter stringWriter = new StringWriter()) {
            write(outputBuilder, stringWriter);
            return stringWriter.toString();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private static final int NO_GUIDE = -1;
    private static final int LINE_SPLIT = -2;

    // guideIndex -1 is used for no tabs at all
    // guideIndex == -2 is for line split without guides
    // endWithNewline forces a newline at the end of the guide
    static class Tab {
        final int indent;
        final int guideIndex;
        int countLines;

        Tab(int indent, int guideIndex) {
            this.indent = indent;
            this.guideIndex = guideIndex;
        }
    }

    // guides typically organised as  ( S int i, M int j, M int k E )

    public void write(OutputBuilder outputBuilder, Writer writer) throws IOException {
        List<OutputElement> list = new ArrayList<>(outputBuilder.list);
        Stack<Tab> tabs = new Stack<>();
        int pos = 0;
        int end = list.size();
        boolean writeNewLine = true; // only indent when a newline was written
        while (pos < end) {
            int indent = tabs.isEmpty() ? 0 : tabs.peek().indent;

            // we should only indent if we wrote a new line
            if (writeNewLine) indent(indent, writer);
            int lineLength = options.lengthOfLine() - indent;
            CurrentExceeds lookAhead = lookAhead(list, pos, lineLength);

            boolean lineSplit = LINE_SPLIT == (tabs.isEmpty() ? NO_GUIDE : tabs.peek().guideIndex);
            if (lookAhead.exceeds != null) {
                pos = handleExceeds(lookAhead, lineSplit, tabs, list, writer, pos);
                writeNewLine = true;
            } else if (lookAhead.current == null) {
                // direct newline hit
                writeNewLine = false;
                pos++;
            } else {
                writeLine(list, writer, pos, lookAhead.current.pos);
                if (lineSplit) {
                    tabs.pop();
                }
                pos = lookAhead.current.pos + 1; // move one step beyond

                // tab management: note that exceeds is never a guide.
                Guide guide = lookAhead.current.guide;
                if (guide != null) {
                    writeNewLine = handleGuide(guide, tabs);
                } else {
                    writeNewLine = true;
                }
            }
            if (writeNewLine) writer.write("\n");
        }
        if (!writeNewLine) writer.write("\n"); // end on a newline
    }

    private int handleExceeds(CurrentExceeds lookAhead, boolean lineSplit, Stack<Tab> tabs,
                              List<OutputElement> list, Writer writer, int pos) throws IOException {
        if (!lineSplit) {
            int previousIndent = tabs.isEmpty() ? 0 : tabs.peek().indent;
            tabs.add(new Tab(previousIndent + options.tabsForLineSplit() * options.spacesInTab(),
                    LINE_SPLIT));
        }
        if (lookAhead.current != null) {
            writeLine(list, writer, pos, lookAhead.current.pos);
            return lookAhead.exceeds.pos;
        } else {
            writeLine(list, writer, pos, lookAhead.exceeds.pos);
            return lookAhead.exceeds.pos + 1;
        }
    }

    private boolean handleGuide(Guide guide, Stack<Tab> tabs) {
        if (guide.position() == Guide.Position.START) {
            int previousIndent = tabs.isEmpty() ? 0 : tabs.peek().indent;
            tabs.add(new Tab(previousIndent + guide.tabs() * options.spacesInTab(),
                    guide.index()));
            // do we need a newline? in the case of ending on a start after {, (, we do
            // in the case of the start of an annotation sequence, we don't
            return guide.startWithNewLine();
        }

        // MID or END
        while (!tabs.isEmpty() && tabs.peek().guideIndex == LINE_SPLIT) {
            tabs.pop();
        }
        assert tabs.isEmpty() || tabs.peek().guideIndex == guide.index();

        if (guide.position() == Guide.Position.END) {
            // tabs can already be empty if the writeLine ended and left an ending
            // guide as the very last one
            if (!tabs.isEmpty()) tabs.pop();
            return guide.endWithNewLine();
        }
        // MID
        if (!tabs.isEmpty()) {
            tabs.peek().countLines = 0; // reset
        }
        return true;
    }

    /**
     * Makes no decisions, simply writes all non-guides from start to end inclusive.
     *
     * @param list   the source
     * @param writer the output writer
     * @param start  the position in the source to start
     * @param end    the position in the source to end
     * @throws IOException when something goes wrong writing to <code>writer</code>
     */
    void writeLine(List<OutputElement> list, Writer writer, int start, int end) throws IOException {
        try {
            forward(list, forwardInfo -> {
                try {
                    if (forwardInfo.guide == null) {
                        writer.write(forwardInfo.string);
                    }
                    return forwardInfo.pos == end;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, start, Math.max(100, options.lengthOfLine() * 3) / 2);
        } catch (RuntimeException e) {
            throw new IOException(e);
        }
    }

    void indent(int spaces, Writer writer) throws IOException {
        for (int i = 0; i < spaces; i++) writer.write(" ");
    }

    record CurrentExceeds(ForwardInfo current, ForwardInfo exceeds) {
    }

    static class GuideOnStack {
        final ForwardInfo forwardInfo;
        int countMid;

        GuideOnStack(ForwardInfo forwardInfo) {
            this.forwardInfo = forwardInfo;
        }
    }

    /**
     * Tries to fit all the remaining output elements on a single line.
     * If that works, then the last element is returned.
     * If a newline is encountered, then the newline is returned.
     * If not everything fits on a line, and the guides allow it, we return a guide position,
     * which can be START, MID or END
     */

    CurrentExceeds lookAhead(List<OutputElement> list, int start, int lineLength) {
        AtomicReference<ForwardInfo> currentForwardInfo = new AtomicReference<>();
        AtomicReference<ForwardInfo> prioritySplit = new AtomicReference<>();
        AtomicReference<ForwardInfo> exceeds = new AtomicReference<>();
        Stack<GuideOnStack> startOfGuides = new Stack<>();
        forward(list, forwardInfo -> {
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
                if (!forwardInfo.symbol && !forwardInfo.isGuide()) {
                    // mark that we're going over the limit
                    exceeds.set(forwardInfo);
                    assert forwardInfo.guide == null;
                    return true;
                }
            }
            currentForwardInfo.set(forwardInfo);

            if (forwardInfo.isGuide()) {
                Guide guide = forwardInfo.guide;
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
                        assert startOfGuides.peek().forwardInfo.guide.index() == guide.index();
                        startOfGuides.peek().countMid++;
                    }
                    case END -> {
                        if (startOfGuides.isEmpty()) {
                            return true; // stop
                        }
                        assert startOfGuides.peek().forwardInfo.guide.index() == guide.index();
                        startOfGuides.pop();
                    }
                }
            }
            OutputElement outputElement = list.get(forwardInfo.pos);
            return outputElement == Space.NEWLINE; // stop on newline, otherwise continue
        }, start, Math.max(100, lineLength * 3) / 2);
        // both can be null, when the forward method immediately hits a newline
        return new CurrentExceeds(currentForwardInfo.get(), exceeds.get());
    }

    /**
     * We accept priority splits when the only other splits have started at the beginning,
     * and no MIDs have been encountered for them
     */
    private boolean acceptPriority(Stack<GuideOnStack> startOfGuides) {
        return startOfGuides.stream().allMatch(guideOnStack -> guideOnStack.forwardInfo.chars == 0 &&
                guideOnStack.countMid == 0);
    }

    record ForwardInfo(int pos, int chars, String string, Split before, Guide guide, boolean symbol) {
        public boolean isGuide() {
            return string == null;
        }

        public int charsPlusString() {
            return chars + (string == null ? 0 : string.length());
        }
    }

    /**
     * Algorithm to iterate over the output elements.
     *
     * @param list     the source
     * @param writer   all forward info sent to this writer; it returns true when this algorithm needs to stop
     * @param start    position in the list where to start
     * @param maxChars some sort of line length
     * @return true when interrupted by a "true" from the writer; false in all other cases (end of list, exceeding maxChars,
     * reaching maxChars using a guide)
     */
    boolean forward(List<OutputElement> list, Function<ForwardInfo, Boolean> writer, int start, int maxChars) {
        OutputElement outputElement;
        int pos = start;
        int chars = 0;
        int end = list.size();

        ElementarySpace lastOneWasSpace = ElementarySpace.NICE; // used to avoid writing double spaces
        Split split = Split.NEVER;
        boolean wroteOnce = false; // don't write a space at the beginning of the line
        boolean allowBreak = false; // write until the first allowed space
        while (pos < end && ((outputElement = list.get(pos)) != Space.NEWLINE)) {
            String string;

            Split splitAfterWriting = Split.NEVER;
            ElementarySpace spaceAfterWriting = ElementarySpace.RELAXED_NONE;
            if (outputElement instanceof Symbol symbol) {
                split = symbol.left().split;
                lastOneWasSpace = combine(lastOneWasSpace, symbol.left().elementarySpace(options));
                string = symbol.symbol();
                spaceAfterWriting = symbol.right().elementarySpace(options);
                splitAfterWriting = symbol.right().split;
                if (split == Split.NEVER) allowBreak = false;
            } else if (outputElement instanceof Guide) {
                string = "";
            } else {
                string = outputElement.write(options);
            }
            // check for double spaces
            if (outputElement instanceof Space space) {
                lastOneWasSpace = combine(lastOneWasSpace, space.elementarySpace(options));
                split = split.easiest(space.split);
                allowBreak |= outputElement != Space.NONE;
            } else if (outputElement instanceof Guide guide) {
                if (chars >= maxChars) return false;
                // empty string indicates that there is a Guide on this position
                // split means nothing here
                if (writer.apply(new ForwardInfo(pos, chars, null, Split.NEVER, guide, false))) return true;
            } else if (string.length() > 0) {
                boolean writeSpace = lastOneWasSpace != ElementarySpace.NONE &&
                        lastOneWasSpace != ElementarySpace.RELAXED_NONE && wroteOnce;
                int stringLen = string.length();
                int goingToWrite = stringLen + (writeSpace ? 1 : 0);

                if (chars + goingToWrite > maxChars && allowBreak && wroteOnce) {// don't write anymore...
                    return false;
                }

                String stringToWrite = writeSpace ? (" " + string) : string;
                if (writer.apply(new ForwardInfo(pos, chars, stringToWrite, split, null,
                        outputElement instanceof Symbol))) return true;
                lastOneWasSpace = spaceAfterWriting;
                split = splitAfterWriting;
                wroteOnce = true;
                allowBreak = split != Split.NEVER;
                chars += stringLen + (writeSpace ? 1 : 0);
            }
            ++pos;
        }
        return false;
    }

    // main point: once we have an enforced ONE, wo do not let go
    // otherwise we step to the spacing of the next one
    private static ElementarySpace combine(ElementarySpace s1, ElementarySpace s2) {
        if (s1 == ElementarySpace.ONE || s1 == ElementarySpace.NONE) {
            return s1;
        }
        return s2;
    }
}
