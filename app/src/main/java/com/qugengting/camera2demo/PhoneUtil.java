package com.qugengting.camera2demo;

import android.content.Context;
import android.content.res.Resources;

/**
 * @author:xuruibin
 * @date:2020/8/3 Description:
 */
public class PhoneUtil {

    /**
     * @param dpVal
     * @return 根据设备的分辨率从 dp 的单位 转成为 px(像素)
     */
    public static int dp2px(int dpVal) {
        return Math.round(dpVal * Resources.getSystem().getDisplayMetrics().density);
    }
}
