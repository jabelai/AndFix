package com.alipay.euler.andfix.hook;


import android.util.Log;

/**
 * Define hook method by annotation
 *
 * <p>
 * Created by jabe on 6/1/16.
 */
public class HookDefine {

    @Hook("android.util.Log::e@java.lang.String#java.lang.String")
    public static int Log_error(String tag, String msg) {

        HookManager.callOrigin(null, tag, msg);

        return Log.i("hook", msg);
    }


    /**
     *
     * 此方法用于备份被hook的函数
     *
     * @param tag
     * @param msg
     * @return
     */
    @HookBackup("android.util.Log::e@java.lang.String#java.lang.String")
    public static int Log_error_backup(String tag, String msg) {
        return 0;
    }

}
