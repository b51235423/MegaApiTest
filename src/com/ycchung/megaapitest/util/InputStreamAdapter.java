package com.ycchung.megaapitest.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by ben-chung on 2016/8/30.
 */

public abstract class InputStreamAdapter extends InputStream {
    private static final String TAG = "InputStreamAdapter";

    public int available() throws IOException {
        Log.w(TAG, "available UnsupportedOperationException");
        throw new UnsupportedOperationException();
    }

    public void close() throws IOException {
    }

    public void mark(int readlimit) {
        Log.w(TAG, "mark UnsupportedOperationException");
        throw new UnsupportedOperationException();
    }

    public boolean markSupported() {
        Log.w(TAG, "markSupported UnsupportedOperationException");
        throw new UnsupportedOperationException();
    }

    @Override
    public int read() throws IOException {
        byte[] buffer = new byte[1];
        int n = read(buffer);
        if (n == 1) return (int) buffer[0];
        else throw new IOException();
    }

    /**
     * Equivalent to {@code read(buffer, 0, buffer.length)}.
     */
    public int read(byte[] buffer) throws IOException {
        return read(buffer, 0, buffer.length);
    }

    @Override
    public abstract int read(byte[] buffer, int offset, int count) throws IOException;

    public synchronized void reset() throws IOException {
        Log.w(TAG, "reset UnsupportedOperationException");
        throw new UnsupportedOperationException();
    }

    public long skip(long byteCount) throws IOException {
        Log.w(TAG, "skip UnsupportedOperationException");
        throw new UnsupportedOperationException();
    }
}
