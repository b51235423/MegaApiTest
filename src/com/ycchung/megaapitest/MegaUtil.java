package com.ycchung.megaapitest;

import com.ycchung.megaapitest.util.Log;
import com.ycchung.megaapitest.util.OutputStreamAdapter;
import com.ycchung.megaapitest.util.Util;
import org.json.JSONException;
import org.json.JSONObject;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.util.*;

/**
 * Created by ycchung on 2017/3/24.
 */
public class MegaUtil {
    private static final String TAG = "mu" + Util.APP_TAG_POSTFIX;
    //
    public static final String ENCRYPTED_NODE_PREFIX = "EncryptedNode_",
            ENCRYPTED_FOLDER_PREFIX = "EncryptedFolder_";
    //
    public static final boolean DBG = true;
    //
    private static final int AES_BLOCK_SIZE = 16;
    private static final String IV = "\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0\0";

    static Cipher getCipher(String transformation, int mode, Key key, AlgorithmParameterSpec spec) {
        try {
            Cipher c = Cipher.getInstance(transformation);
            if (spec != null) c.init(mode, key, spec);
            else c.init(mode, key);
            return c;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException |
                InvalidAlgorithmParameterException e) {
            Log.e(TAG, "", e);
        }
        return null;
    }

    // http://stackoverflow.com/questions/23743842/random-access-inputstream-using-aes-ctr-mode-in-android
    public static final Cipher getCipherWithOffset(int mode, SecretKey key, IvParameterSpec iv, long offset)
            throws InvalidAlgorithmParameterException, InvalidKeyException, ShortBufferException {
        Cipher c = null;
        try {
            c = Cipher.getInstance("AES/CTR/nopadding");
            // if (!c.getAlgorithm().toUpperCase().startsWith("AES/CTR"))
            //     throw new IllegalArgumentException("Invalid algorithm, only AES/CTR mode supported");
            if (offset < 0) throw new IllegalArgumentException("Invalid offset");
            int skip = (int) (offset % AES_BLOCK_SIZE);
            c.init(mode, key, ivWithOffset(iv, offset - skip, AES_BLOCK_SIZE));
            byte[] skipBuffer = new byte[skip];
            c.update(skipBuffer, 0, skip, skipBuffer);
            Arrays.fill(skipBuffer, (byte) 0);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            Log.e(TAG, "", e);
        }
        return c;
    }

    public static IvParameterSpec ivWithOffset(IvParameterSpec iv, long blockOffset, int aesBlockSize) {
        BigInteger ivBI = new BigInteger(1, iv.getIV());
        BigInteger ivForOffsetBI = ivBI.add(BigInteger.valueOf(blockOffset / aesBlockSize));
        byte[] ivForOffsetBA = ivForOffsetBI.toByteArray();
        IvParameterSpec ivForOffset;
        if (ivForOffsetBA.length >= aesBlockSize) {
            ivForOffset = new IvParameterSpec(ivForOffsetBA, ivForOffsetBA.length - aesBlockSize, aesBlockSize);
        } else {
            byte[] ivForOffsetBASized = new byte[aesBlockSize];
            System.arraycopy(ivForOffsetBA, 0, ivForOffsetBASized, aesBlockSize - ivForOffsetBA.length, ivForOffsetBA.length);
            ivForOffset = new IvParameterSpec(ivForOffsetBASized);
        }
        return ivForOffset;
    }

    static String base64UrlEncode(byte[] data) {
        String result = com.ycchung.megaapitest.util.Base64.encodeToString(data, com.ycchung.megaapitest.util.Base64.DEFAULT);
        result = result.replace('+', '-').replace('/', '_').replace("=", "");
        return result.trim();
    }

    static byte[] base64UrlDecode(String data) {
        if (data == null) data = "";
        while (data.length() % 4 != 0) data += "=";
        data = data.replace('-', '+').replace('_', '/').replace(",", "");
        return com.ycchung.megaapitest.util.Base64.decode(data, com.ycchung.megaapitest.util.Base64.DEFAULT);
    }

    static int[] stringToA32(String data) {
        if (data == null) data = "";
        while (data.length() % 4 != 0) data += "\0";
        return bytesToA32(data.getBytes());
    }

    static byte[] a32ToBytes(int[] a32) {
        ByteBuffer b = ByteBuffer.wrap(new byte[a32.length * 4]);
        for (int i : a32) b.putInt(i);
        return b.array();
    }

    static int[] bytesToA32(byte[] data) {
        ByteBuffer b = ByteBuffer.wrap(data);
        int[] result = new int[data.length / 4];
        for (int i = 0; i < result.length; ++i) result[i] = b.getInt();
        return result;
    }

    static String a32ToBase64(int[] a32) {
        return base64UrlEncode(a32ToBytes(a32));
    }

    static int[] base64ToA32(String data) {
        return bytesToA32(base64UrlDecode(data));
    }

    // use AES CBC to encrypt
    static byte[] encrypt(byte[] data, byte[] secretKey) {
        try {
            return getCipher("AES/CBC/NOPADDING", Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey, "AES"),
                    new IvParameterSpec(IV.getBytes())).doFinal(data);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            Log.e(TAG, "", e);
        }
        return new byte[0];
    }

    // use AES CBC to decrypt
    static byte[] decrypt(byte[] data, byte[] secretKey) {
        try {
            return getCipher("AES/CBC/NOPADDING", Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey, "AES"),
                    new IvParameterSpec(IV.getBytes())).doFinal(data);
        } catch (BadPaddingException | IllegalBlockSizeException e) {
            Log.e(TAG, "", e);
        }
        return new byte[0];
    }

    static int[] encryptA32(int[] data, int[] secretKey) {
        byte[] result = ByteBuffer.wrap(encrypt(a32ToBytes(data), a32ToBytes(secretKey))).array();
        return bytesToA32(result);
    }

    static int[] decryptA32(int[] data, int[] secretKey) {
        byte[] result = ByteBuffer.wrap(decrypt(a32ToBytes(data), a32ToBytes(secretKey))).array();
        return bytesToA32(result);
    }

    static int[] prepareKeyA32(int[] password) {
        int[] pkey = {0x93C467E3, 0x7DB0C7A4, 0xD1BE3F81, 0x0152CB56};
        for (int r = 0; r < 0x10000; r++) {
            for (int j = 0; j < password.length; j += 4) {
                int[] key = {0, 0, 0, 0};
                for (int i = 0; i < 4; i++) if (i + j < password.length) key[i] = password[i + j];
                pkey = encryptA32(pkey, key);
            }
        }
        return pkey;
    }

    static int[] encryptKeyA32(int[] data, int[] secretKey) {
        IntBuffer result = IntBuffer.allocate(data.length);
        for (int i = 0; i < data.length; i += 4)
            result.put(encryptA32(Arrays.copyOfRange(data, i, i + 4), secretKey));
        return result.array();
    }

    static int[] decryptKeyA32(int[] data, int[] secretKey) {
        IntBuffer result = IntBuffer.allocate(data.length);
        for (int i = 0; i < data.length; i += 4)
            result.put(decryptA32(Arrays.copyOfRange(data, i, i + 4), secretKey));
        return result.array();
    }

    // multiple precision integer to big integer
    static BigInteger mpiToInt(byte[] data) {
        // first two bit is number of digits?
        if (data.length > 2) data = Arrays.copyOfRange(data, 2, data.length);
        else return BigInteger.ZERO;
        return new BigInteger(bytesToHex(data), 16);
    }

    static String stringHash(String email, int[] aeskey) {
        int[] s32 = stringToA32(email);
        int[] h32 = {0, 0, 0, 0};
        for (int i = 0; i < s32.length; i++) h32[i % 4] ^= s32[i];
        for (int r = 0; r < 0x4000; r++) h32 = encryptA32(h32, aeskey);
        return a32ToBase64(new int[]{h32[0], h32[2]});
    }

    static byte[] hexToBytes(String hex) {
        if (hex.length() % 2 != 0) hex = "0" + hex;
        ByteBuffer b = ByteBuffer.allocate(hex.length() / 2);
        for (int i = 0; i < hex.length(); i += 2) b.put((byte) Integer.parseInt(hex.substring(i, i + 2), 16));
        return b.array();
    }

    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    static JSONObject decryptAttributes(byte[] data, int[] key) {
        String result = new String(decrypt(data, a32ToBytes(key))).trim();
        try {
            if (result.startsWith("MEGA")) return new JSONObject(result.substring(4));
            else return new JSONObject(result);
        } catch (JSONException e) {
            Log.e(TAG, "", e);
        }
        return new JSONObject();
    }

    static byte[] encryptAttributes(JSONObject data, int[] key) {
        String s = "MEGA" + data;
        while (s.length() % 16 != 0) s += "\0";
        return encrypt(s.getBytes(), a32ToBytes(key));
    }

    static Long[] CHUNKS_START = generateChunks(68719476736L);

    static Long[] generateChunks(long size) {
        long sum = 0;
        List<Long> m = new LinkedList<>();
        while (sum < size) {
            m.add(sum);
            sum += 131072 * Math.min(m.size(), 8);
        }
        return m.toArray(new Long[m.size()]);
    }

    // TODO: add meta mac process for reader?
    static class MetaMacProcessor extends OutputStreamAdapter {
        int mChunkCount = 1;
        long mCount = 0, mNextChunkStart = CHUNKS_START[1];
        OutputStream mOutput = null;
        int[] mMac = new int[]{0, 0, 0, 0}, mKey = null, mParam = null, mMetaMac = null, mChunkMac = null;

        public MetaMacProcessor(OutputStream output, int[] param) {
            mOutput = output;
            mKey = Arrays.copyOfRange(param, 0, 4);
            mParam = param;
            mChunkMac = new int[]{mParam[4], mParam[5], mParam[4], mParam[5]};
        }

        void update(byte[] data, int offset, int length) {
            for (int i = offset; i < length; i += 16) {
                byte[] block = new byte[16];
                for (int j = i; j < Math.min(i + 16, data.length); ++j) block[j - i] |= data[j];
                int[] blockA32 = bytesToA32(block);
                mChunkMac = encryptA32(new int[]{mChunkMac[0] ^ blockA32[0], mChunkMac[1] ^ blockA32[1],
                        mChunkMac[2] ^ blockA32[2], mChunkMac[3] ^ blockA32[3]}, mKey);
                mCount += 16;
            }
            if (mCount >= mNextChunkStart) {
                updateMac();
                mChunkMac = new int[]{mParam[4], mParam[5], mParam[4], mParam[5]};
                mNextChunkStart = CHUNKS_START[++mChunkCount];
            }
        }

        void updateMac() {
            if (DBG) Log.d(TAG, "updateMac mCount:" + mCount + " mNextChunkStart:" + mNextChunkStart);
            mMac = encryptA32(new int[]{mMac[0] ^ mChunkMac[0], mMac[1] ^ mChunkMac[1],
                    mMac[2] ^ mChunkMac[2], mMac[3] ^ mChunkMac[3]}, mKey);
        }

        @Override
        public void write(byte[] buffer, int offset, int count) throws IOException {
            mOutput.write(buffer, offset, count);
            update(buffer, offset, count);
        }

        @Override
        public void close() throws IOException {
            mOutput.close();
            updateMac();
            mMetaMac = new int[]{mMac[0] ^ mMac[1], mMac[2] ^ mMac[3]};
        }

        public int[] metaMac() {
            return mMetaMac;
        }

        public int[] newKey() {
            return new int[]{mParam[0] ^ mParam[4], mParam[1] ^ mParam[5], mParam[2] ^ mMetaMac[0], mParam[3] ^ mMetaMac[1], mParam[4], mParam[5], mMetaMac[0], mMetaMac[1]};
        }
    }
//
//    static class Counter extends OutputStreamAdapter {
//        int mChunk = 1;
//        long mCount = 0;
//        OutputStream mOutput = null;
//
//        public Counter(OutputStream output) {
//            mOutput = output;
//        }
//
//        @Override
//        public void write(byte[] buffer, int offset, int count) throws IOException {
//            mOutput.write(buffer, offset, count);
//            mCount += count;
//            if (mCount >= CHUNKS_START[mChunk]) {
//                ++mChunk;
//                Log.d(TAG, "Counter write mCount:" + mCount);
//            }
//        }
//
//        @Override
//        public void close() {
//            Log.d(TAG, "Counter close mCount:" + mCount);
//        }
//    }
}
