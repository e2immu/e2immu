package org.e2immu.analyser.parser.external.testexample;

public class External_14 {
    private String t; // problem disappears when t not private
    private String f;

    public String method(boolean b, String[] s, String[] a) {
        synchronized (this) {
            if (!b && s != null && s.length == 3) {
                if (a[0].equals(s[0]) && a[1].equals(s[1])) {
                    System.out.println(t);
                    t = s[2];
                    return get();
                }
            }
            return "?";
        }
    }

    public String get() {
        if (f == null) {
            f = "";
        }
        return f;
    }

}
