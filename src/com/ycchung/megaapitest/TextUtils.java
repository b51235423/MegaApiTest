package com.ycchung.megaapitest;

/**
 * Created by ben-chung on 2017/3/23.
 */
public class TextUtils {
    public static boolean isEmpty(CharSequence str) {
        if (str == null || str.length() == 0)
            return true;
        else
            return false;
    }
}
