package org.e2immu.analyser.parser.external.testexample;

public class External_16 {

    private final long b;
    private final String s;

    public External_16(String s) {
        this.s = s;
        b = s.length();
    }

    public String compute(long k) {
        k = Long.parseLong(new StringBuilder().append(k).append(1).reverse().toString());
        StringBuilder sb = new StringBuilder();
        long l;
        while (k > 0) {
            l = k % b;
            sb.append(s.charAt((int) l));
            k = (long) Math.floor((double) k / b);
        }
        return sb.toString();
    }

}
