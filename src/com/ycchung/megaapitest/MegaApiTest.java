package com.ycchung.megaapitest;

import com.ycchung.megaapitest.util.Log;
import com.ycchung.megaapitest.util.Util;

import java.io.*;
import java.util.*;

public class MegaApiTest {
    private static final String TAG = "MegaApiTest";
    private int mCount = 0;

    public static void main(String[] args) {
        new Thread(() -> {
            Log.d(TAG, "MegaApiTest CHUNKS_START length:" + MegaUtil.CHUNKS_START.length);

            // MegaClient client = new MegaClient("email", "password");
            // Log.d(TAG, "MegaApiTest isLoggedIn:" + client.isLoggedIn());

            // testGetRoot(client);

            // testGetPublicFolder(client);

            // testGetPublicNode(client);

            // testPushNodeInRoot(client);

        }).start();
    }

    private static void testGetRoot(MegaClient client) {
        Log.d(TAG, "testGetRoot++");
        for (MegaNode node : client.getNodes()) {
            Log.d(TAG, "testGetRoot json:" + node);
            // try to download a node in root
            if (node.name.contains("testGetRootTestDownload.txt")) {
                try {
                    Log.d(TAG, "testGetRoot try download " + node.name);
                    transfer(client.getReader(node, 0L, null), new FileOutputStream(new File(node.name)));
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "", e);
                }
            }
        }
        Log.d(TAG, "testGetRoot--");
    }

    private static void testGetPublicFolder(MegaClient client) {
        // test for get public folder
        Log.d(TAG, "testGetPublicFolder++");
        String testGetFolderUrl = "https://mega.nz/#F!hgt3BKUK!8si55rI13hG9dpsou476sQ";
        List<MegaNode> nodes = client.getNodes(testGetFolderUrl);
        for (MegaNode node : nodes) {
            Log.d(TAG, "testGetPublicFolder node:" + node);
            // try to download node in public folder
            if (node.name.contains("testGetPublicFolderTestDownload.txt")) {
                try {
                    Log.d(TAG, "testGetPublicFolder try download " + node.name);
                    transfer(client.getReader(node, 0L, null), new FileOutputStream(new File(node.name)));
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "", e);
                }
            }
        }
        Log.d(TAG, "testGetPublicFolder--");
    }

    private static void testGetPublicNode(MegaClient client) {
        // test for get public node
        Log.d(TAG, "testGetPublicNode++");
        String testGetNodeUrl = "https://mega.nz/#!RzZERJJC!t6rd_ocz4evobUGEOa3S6wfT-O-JZhhxyGfG0tvQdrk";
        try {
            MegaNode n = client.getNode(testGetNodeUrl);
            Log.d(TAG, "testGetPublicNode node:" + n);
            // try to download public node
            transfer(client.getReader(n, 0L, null), new FileOutputStream(new File(n.name)));
        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
        Log.d(TAG, "testGetPublicNode--");
    }

    private static void testPushNodeInRoot(MegaClient client) {
        Log.d(TAG, "testPushNodeInRoot++");
        try {
            File file = new File("test.zip");
            Log.d(TAG, "testPushNodeInRoot file.exists:" + file.exists());
            OutputStream output = client.getWriter(file.getName(), file.length(), new MegaClient.OnWriterClosedListener() {
                @Override
                public void onWriterClosed(MegaNode node) {
                    Log.d(TAG, "testPushNodeInRoot node:" + node);
                }
            });
            transfer(new FileInputStream(file), output);
        } catch (Exception e) {
            Log.e(TAG, "", e);
        }
        Log.d(TAG, "testPushNodeInRoot--");
    }

    private static void transfer(InputStream input, OutputStream output) {
        try {
            Util.transfer(input, output);
        } catch (IOException e) {
            Log.e(TAG, "", e);
        }
    }
}