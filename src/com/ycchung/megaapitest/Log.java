package com.ycchung.megaapitest;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by ycchung on 2016/9/7.
 */

// Please remove this class when in Android

public class Log {
    public static void d(String tag, String msg) {
        System.out.println(getTimeString() + " D " + tag + ": " + msg);
    }

    public static void e(String tag, String msg) {
        System.out.println(getTimeString() + " E " + tag + ": " + msg);
    }

    public static void e(String tag, String msg, Throwable tr) {
        tag = getTimeString() + " E " + tag + ": ";
        System.out.println(tag + msg + "\n" + tag + getStackTraceString(tr).replace("\n", "\n" + tag));
    }

    public static String getStackTraceString(Throwable tr) {
        if (tr == null) {
            return "";
        }
        Throwable t = tr;
        while (t != null) {
            if (t instanceof UnknownHostException) {
                return "";
            }
            t = t.getCause();
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }

    public static final String getTimeString() {
        SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");
        return sdf.format(new Date());
    }

    public static void w(String tag, String msg) {
        System.out.println(getTimeString() + " W " + tag + ": " + msg);
    }
}
