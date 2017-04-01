package com.ycchung.megaapitest;

import com.ycchung.megaapitest.util.Util;
import org.json.JSONObject;

import java.io.Serializable;

import static com.ycchung.megaapitest.MegaUtil.*;

/**
 * Created by ycchung on 2017/3/30.
 */

public class MegaNode implements Serializable {
    private static final String TAG = "mu" + Util.APP_TAG_POSTFIX;
    // basic attributes
    int type = -1;
    int[] key = null, iv = null, metaMac = null;
    long size = 0, lastModifiedTime = 0;
    String name = "", id = null, parentId = null, ownerId = null, c = null;
    JSONObject json = null;
    // for public node
    boolean isPublicNode = false;
    // for nodes of public folder
    String folderId = null;

    public MegaNode(JSONObject json, int[] masterKeyA32, String folderId) {
        this.json = json;
        //
        this.folderId = folderId;
        //
        if (!json.has("h")) Log.w(TAG, "MegaNode has(\"h\"):false");
        id = json.optString("h");
        parentId = json.optString("p");
        ownerId = json.optString("u");
        type = json.optInt("t");
        lastModifiedTime = json.optLong("ts");
        if (!json.has("s")) Log.w(TAG, "MegaNode has(\"s\"):false");
        size = json.optLong("s");
        //
        if (!json.has("k")) Log.w(TAG, "MegaNode has(\"k\"):false");
        String k = json.optString("k"), encryptedKey = k.substring(k.indexOf(":") + 1, k.length());
        if (masterKeyA32 == null) {
            isPublicNode = true;
            key = bytesToA32(base64UrlDecode(encryptedKey));
        } else key = decryptKeyA32(base64ToA32(encryptedKey), masterKeyA32);
        //
        if (type == 0) {
            iv = new int[]{key[4], key[5], 0, 0};
            metaMac = new int[]{key[6], key[7]};
            key = new int[]{key[0] ^ key[4], key[1] ^ key[5], key[2] ^ key[6], key[3] ^ key[7]};
        }
        // attribute for public node is "at", for other is "a"
        String at = json.has("at") ? json.optString("at") : json.optString("a");
        JSONObject attributes = decryptAttributes(base64UrlDecode(at), key);
        if (!json.has("k")) Log.w(TAG, "MegaNode attributes has(\"n\"):false");
        name = attributes.optString("n");
        if (!attributes.has("n")) name = (type == 0 ? ENCRYPTED_NODE_PREFIX : ENCRYPTED_FOLDER_PREFIX) + id;
        c = attributes.optString("c");
    }

    @Override
    public String toString() {
        return "id:" + id + " name:" + name + " type:" + type + " size:" + size + " parent:" + parentId +
                " isPublicNode:" + isPublicNode + "\n\tjson:" + json;
    }
}