package org.zero.zeromod;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class MediaActionHook implements IXposedHookZygoteInit{

    @Override
    public void initZygote(StartupParam startupParam) {

        /*
         RIP MediaActionSounds
         {
            SHUTTER_CLICK,
            FOCUS_COMPLETE,
            START_VIDEO_RECORDING,
            STOP_VIDEO_RECORDING
         }
         */
        XposedHelpers.findAndHookMethod("android.media.MediaActionSound", null, "play", "int", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                    param.setResult(null); }
        });
    }
}
