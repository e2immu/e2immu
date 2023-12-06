package org.e2immu.analyser.parser.external.testexample;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class External_4 extends FilterOutputStream {
    private static final byte[] array = {'A', 'B', 'C'};

    private final byte[] buffer = new byte[3];
    private final boolean l;
    private int s;
    private int p;

    public External_4(OutputStream out, boolean b) {
        super(out);
        this.l = b;
    }

    private int checkLine(byte[] b, int off) {
        if (++s == 76) {
            s = 0;
            b[off] = '\r';
            b[off + 1] = '\n';
            return 2;
        }
        return 0;
    }

    @Override
    public void close() throws IOException {
        if (p > 0) {
            byte[] output = new byte[6];
            int len = encode(output);
            out.write(output, 0, len);
        }
    }

    private int encode(byte[] b) {
        int i = 0; // 00
        b[i++] = array[(buffer[0] >> 2) & 0x3f];
        if (!l) {
            i += checkLine(b, i);
        }
        b[i++] = array[((buffer[0] << 4) & 0x3f) | ((buffer[1] >> 4) & 0x0f)];
        if (!l) {
            i += checkLine(b, i);
        }
        b[i++] = p > 1 ? array[((buffer[1] << 2) & 0x3f) | ((buffer[2] >> 6) & 0x03)] : (byte) '=';
        if (!l) {
            i += checkLine(b, i);
        }
        b[i++] = p > 2 ? array[buffer[2] & 0x3f] : (byte) '=';
        if (!l) {
            i += checkLine(b, i);
        }
        for (int j = 0; j < buffer.length; buffer[j++] = 0) {
            ;
        }
        p = 0; // 10
        return i; // 11
    }
}
