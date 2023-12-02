package org.e2immu.analyser.parser.external.testexample;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class External_4 extends FilterOutputStream {
    private static final byte[] alphabet = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'};

    private final byte[] buffer = new byte[3]; // One encoding quantum.
    private final boolean oneLine;
    private int lineSize;
    private int position;

    public External_4(OutputStream out, boolean oneLine) {
        super(out);
        this.oneLine = oneLine;
    }

    private int checkLine(byte[] b, int off) {
        if (++lineSize == 76) {
            lineSize = 0;
            b[off] = '\r';
            b[off + 1] = '\n';
            return 2;
        }
        return 0;
    }

    @Override
    public void close() throws IOException {
        if (position > 0) {
            byte[] output = new byte[6];
            int len = encode(output);
            out.write(output, 0, len);
        }
    }

    private int encode(byte[] b) {
        int i = 0;
        b[i++] = alphabet[(buffer[0] >> 2) & 0x3f];
        if (!oneLine) {
            i += checkLine(b, i);
        }
        b[i++] = alphabet[((buffer[0] << 4) & 0x3f) | ((buffer[1] >> 4) & 0x0f)];
        if (!oneLine) {
            i += checkLine(b, i);
        }
        b[i++] = position > 1 ? alphabet[((buffer[1] << 2) & 0x3f) | ((buffer[2] >> 6) & 0x03)] : (byte) '=';
        if (!oneLine) {
            i += checkLine(b, i);
        }
        b[i++] = position > 2 ? alphabet[buffer[2] & 0x3f] : (byte) '=';
        if (!oneLine) {
            i += checkLine(b, i);
        }
        for (int j = 0; j < buffer.length; buffer[j++] = 0) {
            ;
        }
        position = 0;
        return i;
    }
}
