package org.e2immu.analyser.util;

public enum PackedInt {

    // keep these in the order they appear! .ordinal() is being used.
    STATIC_METHOD_CALL_OR_ANNOTATION('A'),
    EXPRESSION('E'),
    STATIC_METHOD('S'),
    METHOD('M'),
    FIELD('F'),
    HIERARCHY('H');

    public final char code;

    PackedInt(char code) {
        this.code = code;
    }

    public int of(int i) {
        return Math.min(i, GROUP_MASK) << (BITS_PER_GROUP * ordinal());
    }

    public static final int GROUPS = PackedInt.values().length;
    public static final int BITS_PER_GROUP = 5; // 6 groups * 5 bits < 32!
    public static final int MAX_PER_GROUP = 1 << BITS_PER_GROUP;
    private static final int GROUP_MASK = MAX_PER_GROUP - 1;
    private static final int BITS = GROUPS * BITS_PER_GROUP;

    public static int sum(int i1, int i2) {
        int sum = 0;
        for (int shift = 0; shift < BITS; shift += BITS_PER_GROUP) {
            int v1 = (i1 >> shift) & GROUP_MASK;
            int v2 = (i2 >> shift) & GROUP_MASK;
            int s = Math.min(v1 + v2, GROUP_MASK);
            sum += (s << shift);
        }
        return sum;
    }

    public static String nice(int i) {
        if (i == 0) return "0";
        StringBuilder sb = new StringBuilder();
        boolean empty = true;
        for (int g = GROUPS - 1; g >= 0; g--) {
            int shift = BITS_PER_GROUP * g;
            int v = (i >> shift) & GROUP_MASK;
            if (v > 0) {
                if (empty) {
                    empty = false;
                } else {
                    sb.append(' ');
                }
                sb.append(values()[g].code);
                sb.append(":");
                sb.append(v);
            }
        }
        return sb.toString();
    }
}
