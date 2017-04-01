package com.ycchung.megaapitest.util;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Iterator;

/**
 * Created by ycchung on 2017/3/23.
 */
public class Util {
    public static String APP_TAG_POSTFIX = "_mdbg";
    private static final String TAG = "Util" + APP_TAG_POSTFIX;
    public static final int TRANSFER_CHUNK_SIZE = 8192;

    public static boolean arrayCompare(int[] a1, int[] a2) {
        for (int i = 0; i < a1.length; ++i) if (a1[i] != a2[i]) return false;
        return true;
    }

    public static long transfer(InputStream input, OutputStream output) throws IOException {
        long sum = 0;
        int n = 0;
        byte[] buffer = new byte[TRANSFER_CHUNK_SIZE];
        while (n >= 0) {
            if ((n = input.read(buffer)) > 0) {
                sum += n;
                output.write(buffer, 0, n);
            }
        }
        safeClose(input);
        safeClose(output);
        return sum;
    }

    public static String readString(InputStream input) {
        StringBuffer result = new StringBuffer();
        try {
            String line = "";
            BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            while ((line = reader.readLine()) != null) result.append(line);
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "", e);
        } finally {
            if (input != null) safeClose(input);
        }
        return result.toString();
    }

    public static void writeString(OutputStream output, String s) {
        try {
            OutputStreamWriter wr = new OutputStreamWriter(output);
            wr.write(s);
            wr.flush();
            wr.close();
        } catch (IOException e) {
            Log.e(TAG, "", e);
        } finally {
            if (output != null) safeClose(output);
        }
    }

    public static final void safeClose(Object closeable) {
        // Log.w(TAG, "safeClose o:" + closeable + " " + closeable.getClass().getName());
        try {
            if (closeable != null) {
                if (closeable instanceof Closeable) ((Closeable) closeable).close();
                else if (closeable instanceof AutoCloseable) ((AutoCloseable) closeable).close();
                else if (closeable instanceof Socket) ((Socket) closeable).close();
                else if (closeable instanceof ServerSocket) ((ServerSocket) closeable).close();
                else throw new IllegalArgumentException("Unknown object to close");
            }
        } catch (Exception e) {
            Log.e(TAG, "failed to close " + closeable.getClass().getName(), e);
        }
    }

    public static Iterable<JSONObject> iterator(final JSONArray array) {
        return new Iterable<JSONObject>() {
            @Override
            public Iterator<JSONObject> iterator() {
                return new Iterator<JSONObject>() {
                    int mIndex = -1;

                    @Override
                    public boolean hasNext() {
                        if (array != null && mIndex < array.length() - 1) return true;
                        return false;
                    }

                    @Override
                    public JSONObject next() {
                        if (hasNext()) try {
                            return (JSONObject) array.get(++mIndex);
                        } catch (JSONException e) {
                            Log.e(TAG, "", e);
                        }
                        return null;
                    }
                };
            }
        };
    }
}
