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
import java.util.concurrent.atomic.AtomicInteger;
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
    private record Tab(int tabs, int guideIndex, boolean endWithNewline) {
    }

    // guides typically organised as  ( S int i, M int j, M int k E )

    public void write(OutputBuilder outputBuilder, Writer writer) throws IOException {
        List<OutputElement> list = new ArrayList<>(outputBuilder.list);
        int pos = 0;
        int end = list.size();
        Stack<Tab> tabs = new Stack<>();
        boolean writeNewLine = true;
        while (pos < end) {
            int spaces = (tabs.isEmpty() ? 0 : tabs.peek().tabs) * options().spacesInTab();
            int currentGuide = (tabs.isEmpty() ? NO_GUIDE : tabs.peek().guideIndex);
            boolean lineSplit = currentGuide == LINE_SPLIT;

            // we should only indent if we wrote a new line
            if (writeNewLine) indent(spaces, writer);
            int lineLength = options.lengthOfLine() - spaces;

            // if lookahead <= line length, either everything fits (write until lookahead reached)
            // or there is a guide starting at lookahead
            // if lookahead > line length, write until best break. If there is no line split, start one
            int lookAhead = lookAhead(list.subList(pos, end), lineLength);
            writeNewLine = lookAhead > 0;
            if (lookAhead > 0) {
                int newPos;
                if (lookAhead <= lineLength) {
                    newPos = writeLine(list, writer, pos, lookAhead, tabs);
                    if (lineSplit) {
                        tabs.pop();
                    }
                } else {
                    newPos = writeLine(list, writer, pos, lineLength, tabs);
                    if (!lineSplit) {
                        int prevTabs = tabs.isEmpty() ? 0 : tabs.peek().tabs;
                        tabs.add(new Tab(prevTabs + options.tabsForLineSplit(), LINE_SPLIT, false));
                    }
                }
                pos = newPos;
            } else {
                // skip potential spaces to the next guide (see annotations in testGuide4)
                while (pos < end && !(list.get(pos) instanceof Guide)) pos++;
            }
            if (pos < end) {
                OutputElement outputElement = list.get(pos);
                if (outputElement instanceof Guide guide) {
                    if (guide.position() == Guide.Position.START) {
                        int prevTabs = tabs.isEmpty() ? 0 : tabs.peek().tabs;
                        boolean endWithNewline = guide.symmetricalSplit();
                        tabs.add(new Tab(prevTabs + guide.tabs(), guide.index(), endWithNewline));
                    } else {
                        while (!tabs.isEmpty() && tabs.peek().guideIndex == LINE_SPLIT) {
                            tabs.pop();
                        }
                        // tabs can already be empty if the writeLine ended and left an ending
                        // guide as the very last one
                        if (guide.position() == Guide.Position.END && !tabs.isEmpty() && tabs.peek().guideIndex == guide.index()) {
                            tabs.pop();
                        }
                    }
                    pos++;
                } else if (outputElement == Space.NEWLINE) {
                    pos++;
                }
            }
            if (writeNewLine) writer.write("\n");
        }
    }

    /**
     * @param list     the source
     * @param writer   the output writer
     * @param start    the position in the source to start
     * @param maxChars maximal number of chars to write
     * @param tabs     so that we can close tabs as we see closing guides pass by
     * @return the updated position
     * @throws IOException when something goes wrong writing to <code>writer</code>
     */
    int writeLine(List<OutputElement> list, Writer writer, int start, int maxChars, Stack<Tab> tabs) throws IOException {
        AtomicReference<ForwardInfo> lastForwardInfoSeen = new AtomicReference<>();
        try {
            boolean interrupted = forward(list, forwardInfo -> {
                lastForwardInfoSeen.set(forwardInfo);
                OutputElement outputElement = list.get(forwardInfo.pos);
                if (outputElement == Space.NEWLINE) return true;

                if (outputElement instanceof Guide guide) {
                    if (forwardInfo.isGuide()) {
                        // stop when reaching the guide (typically, MID) computed by lookAhead
                        if (forwardInfo.chars == maxChars) return true;
                        // stop when reaching the end of the guide of the current tab
                        if (guide.position() == Guide.Position.END && !tabs.isEmpty() && tabs.peek().guideIndex == guide.index()) {
                            if (tabs.peek().endWithNewline) return true;
                            tabs.pop();
                        }
                    }
                    // there can be spaces at the same position, after the guide (forwardInfo.pos can be off)
                    return false;
                }
                try {
                    writer.write(forwardInfo.string);
                    return false; // continue
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, start, maxChars);
            if (interrupted) return lastForwardInfoSeen.get().pos;
            // reached the end
            return lastForwardInfoSeen.get().pos + 1;
        } catch (RuntimeException e) {
            throw new IOException(e);
        }
    }

    void indent(int spaces, Writer writer) throws IOException {
        for (int i = 0; i < spaces; i++) writer.write(" ");
    }

    record PosAndGuide(int pos, int guide) {
    }

    /**
     * Returns the number of characters that minimally need to be output until we reach a newline (excluding that newline), or,
     * once beyond the line length (max len minus tabs), the beginning of a Guide
     */

    int lookAhead(List<OutputElement> list, int lineLength) {
        AtomicReference<OutputElement> previousElement = new AtomicReference<>();
        AtomicInteger chars = new AtomicInteger();
        AtomicReference<ForwardInfo> lastForwardInfo = new AtomicReference<>();
        AtomicInteger firstLeftBrace = new AtomicInteger(-1);
        Stack<PosAndGuide> startOfGuides = new Stack<>();
        boolean interrupted = forward(list, forwardInfo -> {
            chars.set(forwardInfo.chars);
            lastForwardInfo.set(forwardInfo);
            OutputElement outputElement = list.get(forwardInfo.pos);
            if (outputElement == Space.NEWLINE) return true; // stop
            if (outputElement instanceof Guide guide) {
                switch (guide.position()) {
                    case START -> {
                        if (firstLeftBrace.get() == -1 && guide.symmetricalSplit()) {
                            firstLeftBrace.set(chars.get());
                        }
                        startOfGuides.push(new PosAndGuide(forwardInfo.chars, guide.index()));
                    }
                    case MID -> {
                        if (startOfGuides.isEmpty() || startOfGuides.peek().guide != guide.index()) {
                            return true; // stop
                        }
                    }
                    case END -> {
                        if (!startOfGuides.isEmpty()) {
                            assert startOfGuides.peek().guide == guide.index();
                            startOfGuides.pop();
                        }
                    }
                }
            }
            if (forwardInfo.chars > lineLength) {
                // exceeding the allowed length of the line... we don't allow if we encountered {
                if (firstLeftBrace.get() >= 0) {
                    chars.set(firstLeftBrace.get());
                } else {
                    //  go to first open guide if present
                    if (!startOfGuides.isEmpty()) {
                        chars.set(startOfGuides.get(0).pos);
                    }
                }
                return true; // stop
            }
            previousElement.set(outputElement);
            return false; // continue
        }, 0, Math.max(100, lineLength * 3) / 2);
        if (interrupted || lastForwardInfo.get() == null || lastForwardInfo.get().string == null) {
            return chars.get();
        }
        // reached the end
        return chars.get() + lastForwardInfo.get().string.length();
    }

    record ForwardInfo(int pos, int chars, String string, Split before) {
        public boolean isGuide() {
            return string == null;
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
            ElementarySpace spaceAfterWriting = ElementarySpace.NONE;
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
            } else if (outputElement instanceof Guide) {
                if (chars >= maxChars) return false;
                // empty string indicates that there is a Guide on this position
                // split means nothing here
                if (writer.apply(new ForwardInfo(pos, chars, null, Split.NEVER))) return true;
            } else if (string.length() > 0) {
                boolean writeSpace = lastOneWasSpace != ElementarySpace.NONE && wroteOnce;
                int stringLen = string.length();
                int goingToWrite = stringLen + (writeSpace ? 1 : 0);

                if (chars + goingToWrite > maxChars && allowBreak && wroteOnce) {// don't write anymore...
                    return false;
                }

                String stringToWrite = writeSpace ? (" " + string) : string;
                if (writer.apply(new ForwardInfo(pos, chars, stringToWrite, split))) return true;
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
        if (s1 == ElementarySpace.ONE) {
            return s1;
        }
        return s2;
    }
}
