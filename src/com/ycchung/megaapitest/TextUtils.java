package com.ycchung.megaapitest;

/**
 * Created by ycchung on 2017/3/23.
 */

// Please remove this class when in Android

public class TextUtils {
    public static boolean isEmpty(CharSequence str) {
        if (str == null || str.length() == 0)
            return true;
        else
            return false;
    }
}
