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

import org.e2immu.analyser.output.*;

import java.util.List;
import java.util.function.Function;

public class Forward {

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
    public static boolean forward(FormattingOptions options,
                           List<OutputElement> list,
                           Function<ForwardInfo, Boolean> writer,
                           int start,
                           int maxChars) {
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

            Split splitAfterWriting;
            ElementarySpace spaceAfterWriting;
            if (outputElement instanceof Symbol symbol) {
                split = symbol.left().split;
                lastOneWasSpace = combine(lastOneWasSpace, symbol.left().elementarySpace(options));
                string = symbol.symbol();
                spaceAfterWriting = symbol.right().elementarySpace(options);
                splitAfterWriting = symbol.right().split;
                if (split == Split.NEVER) allowBreak = false;
            } else {
                spaceAfterWriting = ElementarySpace.RELAXED_NONE;
                splitAfterWriting = Split.NEVER;
                if (outputElement instanceof Guide) {
                    string = "";
                } else {
                    string = outputElement.write(options);
                }
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
