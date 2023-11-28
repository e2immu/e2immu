package org.e2immu.analyser.parser.link.testexample;

// essential to the test a t m are INITIAL, switch;
// error: cannot change LVs anymore
public class Link_0 {
    protected static final int INITIAL = 10;

    protected StringBuilder sb;

    public Link_0() {
        sb = new StringBuilder(INITIAL);
    }

    protected void addNormalized(char[] buff, int start) {
        int j = start;
        int endPos = start + 20;

        for (int i = start;i < endPos;i++) {
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
