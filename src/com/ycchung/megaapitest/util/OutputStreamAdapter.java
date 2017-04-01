package com.ycchung.megaapitest.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by ycchung on 2017/3/28.
 */
public abstract class OutputStreamAdapter extends OutputStream {
    @Override
    public void write(int oneByte) throws IOException {
        byte[] buffer = new byte[]{(byte) oneByte};
        write(buffer);
    }

    @Override
    public abstract void write(byte[] buffer, int offset, int length) throws IOException;
}
