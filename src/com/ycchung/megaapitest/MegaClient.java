package com.ycchung.megaapitest;

import com.ycchung.megaapitest.util.InputStreamAdapter;
import com.ycchung.megaapitest.util.OutputStreamAdapter;
import com.ycchung.megaapitest.util.Util;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPrivateKeySpec;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import static com.ycchung.megaapitest.MegaUtil.*;
import static com.ycchung.megaapitest.TextUtils.isEmpty;
import static com.ycchung.megaapitest.util.Util.iterator;
import static com.ycchung.megaapitest.util.Util.readString;
import static com.ycchung.megaapitest.util.Util.safeClose;

/**
 * Created by ycchung on 2017/3/18.
 */

public class MegaClient {
    private static final String TAG = "mu" + Util.APP_TAG_POSTFIX;
    //
    private static final int CONNECTION_TIMEOUT = 15000;
    private static final int RETRY_TIMES = 16, SEND_REQUEST_RETRY = 4;
    private static final String ERROR_CODE = "__error";
    //
    private int mSequenceNumber = new Random().nextInt();
    // session data
    private int[] mMasterKeyA32 = null;
    private BigInteger[] mPrivateKey = null;
    private String mSessionId = null, mUserEmail = null, mUserPwd = null, mUserId = null;
    private String mRootId = null, mInboxId = null, mRecyclebinId = null;
    private Map<String, int[]> mNodeKeys = new LinkedHashMap<>();

    public MegaClient() {
    }

    public MegaClient(String email, String password) {
        login(email, password);
        if (DBG) Log.d(TAG, "MegaClient login:" + isLoggedIn() + " mSessionId:" + mSessionId);
    }

    public boolean isLoggedIn() {
        return !isEmpty(mSessionId);
    }

    public boolean login(String email, String password) {
        for (int i = 0; i < RETRY_TIMES; ++i) {
            if (DBG) Log.d(TAG, "loginWithRetry mSequenceNumber:" + mSequenceNumber + " retry:" + i);
            int[] passwordAesA32 = prepareKeyA32(stringToA32(password));
            mSessionId = login(email, passwordAesA32);
            if (!isEmpty(mSessionId)) {
                mUserEmail = email;
                mUserPwd = password;
                return true;
            }
            mSequenceNumber = new Random().nextInt();
        }
        return false;
    }

    private String login(String email, int[] passwordAesA32) {
        if (DBG) Log.d(TAG, "login mSequenceNumber:" + mSequenceNumber);
        JSONObject response = null;
        try {
            response = sendLoginRequest(email, passwordAesA32);
            if (DBG) Log.d(TAG, "login response:" + response.toString());
            mUserId = response.getString("u");
            mMasterKeyA32 = decryptKeyA32(base64ToA32(response.getString("k")), passwordAesA32);
            if (response.has("tsid")) {
                String tsid = new String(base64UrlDecode(response.getString("tsid")));
                int[] a1 = encryptKeyA32(stringToA32(tsid.substring(0, 16)), mMasterKeyA32),
                        a2 = stringToA32(tsid.substring(16));
                if (DBG) Log.d(TAG, "login json.tsid:" + response.getString("tsid") + " tsid:" + tsid);
                if (DBG) Log.d(TAG, "login a1:" + Arrays.toString(a1) + " a2:" + Arrays.toString(a2));
                if (Util.arrayCompare(a1, a2)) return response.getString("tsid");
            } else if (response.has("csid")) {
                byte[] encryptedPrivateKey = a32ToBytes(decryptKeyA32(base64ToA32(response.getString("privk")), mMasterKeyA32));
                mPrivateKey = new BigInteger[4];
                for (int i = 0; i < 4; ++i) {
                    int l = ((((int) encryptedPrivateKey[0]) * 256 + ((int) encryptedPrivateKey[1]) + 7) / 8) + 2;
                    mPrivateKey[i] = mpiToInt(Arrays.copyOfRange(encryptedPrivateKey, 0, l));
                    encryptedPrivateKey = Arrays.copyOfRange(encryptedPrivateKey, l, encryptedPrivateKey.length);
                }
                if (DBG) Log.d(TAG, "login csid:" + response.getString("csid"));
                BigInteger encryptedSid = mpiToInt(base64UrlDecode(response.getString("csid")));
                int encryptedSidLength = encryptedSid.toByteArray().length;
                if (DBG) Log.d(TAG, "login enc_sid:" + encryptedSid + " length:" + encryptedSidLength);
                BigInteger modulus = mPrivateKey[0].multiply(mPrivateKey[1]);
                BigInteger privateExponent = mPrivateKey[2];
                BigInteger tempSid = null;
                PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new RSAPrivateKeySpec(modulus, privateExponent));
                tempSid = new BigInteger(getCipher("RSA/ECB/NoPadding", Cipher.DECRYPT_MODE, key, null)
                        .doFinal(encryptedSid.toByteArray()));
                return base64UrlEncode(Arrays.copyOfRange(hexToBytes(tempSid.toString(16)), 0, 43));
            }
        } catch (BadPaddingException | NoSuchAlgorithmException | IllegalBlockSizeException |
                InvalidKeySpecException e) {
            Log.e(TAG, "", e);
        } catch (JSONException e) {
            Log.e(TAG, "", e);
        }
        return null;
    }

    private JSONObject sendLoginRequest(String email, int[] passwordAesA32) throws JSONException {
        return sendRequestWithRetry(new JSONObject().put("a", "us").put("user", email)
                .put("uh", stringHash(email.toLowerCase(), passwordAesA32)));
    }

    // get list of nodes in root folder
    public List<MegaNode> getNodes() {
        return getNodes(null);
    }

    // get list of nodes from public folder or root folder
    public List<MegaNode> getNodes(String url) {
        List result = new LinkedList<>();
        JSONObject response = null;
        String[] s = !isEmpty(url) ? url.split("!") : null;
        if (!isEmpty(url) && s.length < 3) {
            Log.w(TAG, "getNodes bad format url:" + url);
            return result;
        }
        try {
            response = sendGetFolderRequest(!isEmpty(url) ? s[1] : null);
            if (DBG) Log.d(TAG, "getNodes response:" + response.toString());
            for (JSONObject ok : iterator(response.optJSONArray("ok"))) {
                mNodeKeys.put(ok.getString("h"), decryptA32(base64ToA32(ok.getString("k")), mMasterKeyA32));
            }
            JSONArray nodes = response.getJSONArray("f");
            if (!isEmpty(url)) {
                mNodeKeys.put(((JSONObject) nodes.get(0)).getString("h"), bytesToA32(base64UrlDecode(s[2])));
            }
            for (JSONObject node : iterator(nodes)) {
                if (node.has("t") && node.has("h")) {
                    int type = node.optInt("t");
                    if (type == 0 || type == 1) {
                        String[] k = node.optString("k").split(":");
                        String folderId = !isEmpty(url) ? s[1] : null;
                        if (k[0].equals(mUserId)) result.add(new MegaNode(node, mMasterKeyA32, folderId));
                        else result.add(new MegaNode(node, mNodeKeys.get(k[0]), folderId));
                    } else if (type == 2) {
                        mRootId = node.optString("h");
                        if (DBG) Log.d(TAG, "getNodes mRootId:" + mRootId);
                    } else if (type == 3) {
                        mInboxId = node.optString("h");
                        if (DBG) Log.d(TAG, "getNodes mInboxId:" + mInboxId);
                    } else if (type == 4) {
                        mRecyclebinId = node.optString("h");
                        if (DBG) Log.d(TAG, "getNodes mRecyclebinId:" + mRecyclebinId);
                    }
                } else {
                    Log.w(TAG, "getNode has(\"t\"):" + node.has("t") + " has(\"k\"):" + node.has("k") +
                            " has(\"h\"):" + node.has("h"));
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "", e);
        }
        return result;
    }

    private JSONObject sendGetFolderRequest(String folderId) throws JSONException {
        return sendRequestWithRetry(new JSONObject().put("a", "f")
                .put("c", 1).put("r", 1), !isEmpty(folderId) ? "&n=" + folderId : null);
    }

    // get public json
    public MegaNode getNode(String url) {
        JSONObject response = null;
        try {
            String[] s = url.split("!");
            response = sendGetNodeRequest(s[1]);
            if (DBG) Log.d(TAG, "getNode response:" + response);
            return new MegaNode(response.put("h", s[1]).put("k", ":" + s[2]), null, null);
        } catch (JSONException e) {
            Log.e(TAG, "", e);
        }
        return null;
    }

    // get reader for download a node
    public InputStream getReader(MegaNode node, Long from, Long end) {
        if (node == null) {
            Log.w(TAG, "getReader node:" + node);
            return null;
        }
        if (node.type != 0) {
            Log.w(TAG, "getReader node.type:" + node.type);
            return null;
        }
        JSONObject response = null;
        try {
            response = sendGetNodeRequest(node);
            if (DBG) Log.d(TAG, "getNode response:" + response);
            return getReader(response.getString("g"), node.key, node.iv, from, end);
        } catch (JSONException e) {
            Log.e(TAG, "", e);
        }
        return null;
    }

    private InputStream getReader(String url, int[] key, int[] iv, Long from, Long end) {
        if (from == null) from = 0L;
        try {
            String range = from + "-" + (end != null ? end : "");
            Cipher c = getCipherWithOffset(Cipher.DECRYPT_MODE, new SecretKeySpec(a32ToBytes(key), "AES"),
                    new IvParameterSpec(a32ToBytes(iv)), from);
            final HttpURLConnection connection = newConnection(url, "POST", range, true, false);
            final InputStream input = connection.getInputStream();
            return new CipherInputStream(new InputStreamAdapter() {
                @Override
                public int read(byte[] buffer, int offset, int count) throws IOException {
                    int n = input.read(buffer, offset, count);
                    if (n < 0) {
                        safeClose(input);
                        connection.disconnect();
                    }
                    return n;
                }
            }, c);
        } catch (InvalidKeyException | ShortBufferException | InvalidAlgorithmParameterException e) {
            Log.e(TAG, "", e);
        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
        return null;
    }

    private JSONObject sendGetNodeRequest(MegaNode node) throws JSONException {
        String extra = !isEmpty(node.folderId) ? "&n=" + node.folderId : null;
        return sendRequestWithRetry(new JSONObject().put("a", "g").put("g", "1")
                .put(node.isPublicNode ? "p" : "n", (node.id)), extra);
    }

    private JSONObject sendGetNodeRequest(String nodeId) throws JSONException {
        return sendRequestWithRetry(new JSONObject().put("a", "g")
                .put("g", "1").put("p", nodeId));
    }

    // get writer for upload and create a new node in root
    public OutputStream getWriter(String name, long size, OnWriterClosedListener listener) {
        if (!isEmpty(mRootId)) {
            return getWriter(mRootId, name, size, listener);
        } else {
            Log.w(TAG, "getWriter isLoggedIn:" + isLoggedIn() + " mRootId:" + mRootId);
            return null;
        }
    }

    public OutputStream getWriter(String rootId, String name, long size, OnWriterClosedListener listener) {
        if (!isLoggedIn()) {
            Log.w(TAG, "getWriter isLoggedIn:" + isLoggedIn());
            return null;
        }
        JSONObject response = null;
        try {
            response = sendRequestWithRetry(newGetWriterRequest(size));
            if (DBG) Log.d(TAG, "getWriter response:" + response);
            return getWriter(response.getString("p"), rootId, name, listener);
        } catch (JSONException e) {
            Log.e(TAG, "", e);
        }
        return null;
    }

    // get writer for upload and create a new node
    private OutputStream getWriter(String p, final String rootId, final String name, final OnWriterClosedListener listener) {
        try {
            Random rg = new Random();
            int[] param = new int[]{rg.nextInt(), rg.nextInt(), rg.nextInt(), rg.nextInt(), rg.nextInt(), rg.nextInt()},
                    key = Arrays.copyOfRange(param, 0, 4);
            if (DBG) Log.d(TAG, "getWriter key:" + Arrays.toString(param));
            Cipher c = getCipherWithOffset(Cipher.ENCRYPT_MODE, new SecretKeySpec(a32ToBytes(key), "AES"),
                    new IvParameterSpec(a32ToBytes(new int[]{param[4], param[5], 0, 0})), 0);
            final HttpURLConnection connection = newConnection(p + "/0", "POST", null, true, true);
            final MetaMacProcessor mp = new MetaMacProcessor(new CipherOutputStream(connection.getOutputStream(), c), param);
            final OutputStream output = new BufferedOutputStream(mp);
            return new OutputStreamAdapter() {
                @Override
                public void write(byte[] buffer, int offset, int count) throws IOException {
                    output.write(buffer, offset, count);
                }

                @Override
                public void close() throws IOException {
                    safeClose(output);
                    String attr = base64UrlEncode(encryptAttributes(newAttribute(name), key)),
                            keyBase64 = a32ToBase64(encryptKeyA32(mp.newKey(), mMasterKeyA32));
                    if (DBG) Log.d(TAG, "getWtiter close attr:" + attr + " t:" + System.currentTimeMillis());
                    String handle = readString(connection.getInputStream());
                    if (DBG) Log.d(TAG, "getWriter close handle:" + handle + " t:" + System.currentTimeMillis());
                    try {
                        JSONObject newNode = sendWriterCloseRequest(handle, attr, keyBase64, rootId);
                        listener.onWriterClosed(new MegaNode(newNode, mMasterKeyA32, null));
                    } catch (JSONException e) {
                        Log.e(TAG, "", e);
                    }
                    connection.disconnect();
                }
            };
        } catch (InvalidAlgorithmParameterException | ShortBufferException | InvalidKeyException e) {
            Log.e(TAG, "", e);
        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
        return null;
    }

    private JSONObject newGetWriterRequest(long size) {
        try {
            return new JSONObject().put("a", "u").put("s", size);
        } catch (JSONException e) {
            Log.e(TAG, "", e);
        }
        return null;
    }

    private JSONObject sendWriterCloseRequest(String completionHandle, String attributes, String key, String rootId) throws JSONException {
        JSONArray array = new JSONArray();
        array.put(new JSONObject().put("h", completionHandle).put("t", 0).put("a", attributes).put("k", key));
        JSONObject response = sendRequestWithRetry(new JSONObject().put("a", "p").put("t", rootId).put("n", array));
        return response.getJSONArray("f").getJSONObject(0);
    }

    private JSONObject newAttribute(String name) {
        try {
            return new JSONObject().put("n", name);
        } catch (JSONException e) {
            Log.e(TAG, "", e);
        }
        return null;
    }

    private HttpURLConnection newConnection(String url, String method, String range, boolean in, boolean out) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            if (!isEmpty(range)) connection.setRequestProperty("Range", range);
            connection.setDoOutput(out);
            connection.setDoInput(in);
            connection.setUseCaches(false);
            connection.setAllowUserInteraction(false);
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            return connection;
        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
        return null;
    }

    private JSONObject sendRequestWithRetry(JSONObject request) {
        return sendRequestWithRetry(request, null);
    }

    private JSONObject sendRequestWithRetry(JSONObject request, String extra) {
        JSONObject result = new JSONObject();
        for (int i = 0; i < SEND_REQUEST_RETRY; ++i) {
            if (DBG) Log.d(TAG, "sendRequestWithRetry mSequenceNumber:" + mSequenceNumber + " retry:" + i);
            result = sendRequest(request, extra);
            if (result != null && !result.has(ERROR_CODE)) return result;
        }
        return result;
    }

    private JSONObject sendRequest(JSONObject request, String extra) {
        HttpURLConnection connection = null;
        String response = null;
        try {
            String url = "https://g.api.mega.co.nz/cs?id=" + mSequenceNumber;
            if (!isEmpty(mSessionId)) url += "&sid=" + mSessionId;
            if (!isEmpty(extra)) url += extra;
            if (DBG) Log.d(TAG, "sendRequest url:" + url + " data:" + request);
            connection = newConnection(url, "POST", "0-", true, true);
            connection.setRequestProperty("Content-Type", "text/xml");
            // output
            Util.writeString(connection.getOutputStream(), "[" + request + "]");
            // input
            response = readString(connection.getInputStream());
            return new JSONObject(response.toString().substring(1, response.toString().length() - 1));
        } catch (IOException | JSONException e) {
            Log.e(TAG, ERROR_CODE + ":" + response.toString(), e);
        }
        try {
            return new JSONObject().put(ERROR_CODE, response.toString());
        } catch (JSONException e) {
            Log.e(TAG, ERROR_CODE + ":" + response.toString(), e);
        }
        return null;
    }

    public interface OnWriterClosedListener {
        void onWriterClosed(MegaNode node);
    }
}
