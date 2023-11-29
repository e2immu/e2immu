package org.e2immu.analyser.parser.link.testexample;

import org.e2immu.annotation.Independent;
import org.e2immu.annotation.Modified;
import org.e2immu.annotation.NotNull;

// essential to the test a t m are INITIAL, switch;
// error: cannot change LVs anymore
public class Link_0 {
    static final int INITIAL = 10;

    @Modified
    private final StringBuilder sb;

    Link_0() {
        sb = new StringBuilder(INITIAL);
    }

    @Modified
    void method(@NotNull(content = true) @Independent char[] buff, int start) {
        int j = start;
        int endPos = start + 20;

        for (int i = start; i < endPos; i++) {
            switch (buff[i]) {
                case '<':
                    sb.append(buff, j, i - j);
                    sb.append("&lt;");
                    j = i + 1;
                    break;

                default:
                    break;
            }
        }
        sb.append(buff, j, endPos - j);
    }
}
