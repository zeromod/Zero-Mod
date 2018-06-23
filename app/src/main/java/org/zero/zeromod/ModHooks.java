package org.zero.zeromod;

import android.content.Context;
import android.content.res.Resources;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import static de.robv.android.xposed.XposedHelpers.callMethod;
import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.getBooleanField;
import static de.robv.android.xposed.XposedHelpers.getObjectField;

public class ModHooks implements IXposedHookLoadPackage {

    private static final String CLASS_STATUSBAR = "com.android.systemui.statusbar.phone.StatusBar";
    private static final String CLASS_COLLAPSED_SB_FRAGMENT = "com.android.systemui.statusbar.phone.CollapsedStatusBarFragment";
    private static final String CLASS_NOTIF_ICON_CONTAINER = "com.android.systemui.statusbar.phone.NotificationIconContainer";
    private static final String CLASS_PHONESTATE = "com.android.systemui.statusbar.SignalClusterView$PhoneState";
    private ViewGroup mStatusBarView, mIconArea;
    private Context context;
    private TextView mClock;
    private LinearLayout mLayoutCenter;
    private ClassLoader classLoader;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.android.systemui"))
            return;
        classLoader = lpparam.classLoader;

        CustomClock(); //Few lines of code from Gravity Box


        //Hide Roaming in signal cluster
        findAndHookMethod(CLASS_PHONESTATE, classLoader, "apply","boolean",new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                ((ImageView) getObjectField(param.thisObject,"mMobileRoaming")).setVisibility(View.GONE);
                super.afterHookedMethod(param);
            }
        });

        //Disable data toggle off warning
        findAndHookMethod("com.android.systemui.qs.tiles.CellularTile", classLoader, "handleClick", new XC_MethodReplacement() {
            @Override
            protected Object replaceHookedMethod(MethodHookParam param) {
                Object mDataController = getObjectField(param.thisObject, "mDataController");

                if ( (Boolean)callMethod(mDataController, "isMobileDataEnabled") ) {
                    callMethod(mDataController, "setMobileDataEnabled", false);
                } else {
                    callMethod(mDataController, "setMobileDataEnabled", true);
                }
                return null;
            }
        });

        //RIP Screenshot audio
        findAndHookMethod("com.android.systemui.screenshot.SaveImageInBackgroundTask$GlobalScreenshot", classLoader, "startAnimation", "Runnable","int","int","boolean","boolean", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("Panni startAnimation method hooked");
                Object mCameraSound = getObjectField(param.thisObject,"mCameraSound");
                callMethod(mCameraSound,"play","null");
                super.beforeHookedMethod(param);
            }
        });
    }

    private void CustomClock() {

        //Create Status Bar CenterLayout
        findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", classLoader, "setBar", CLASS_STATUSBAR, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                mStatusBarView = (ViewGroup) param.thisObject;
                Resources resources = context.getResources();
                mIconArea = mStatusBarView
                        .findViewById(resources.getIdentifier("system_icon_area", "id", "com.android.systemui"));

                mLayoutCenter = new LinearLayout(context);
                mLayoutCenter.setLayoutParams(new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                mLayoutCenter.setGravity(Gravity.CENTER);

                mStatusBarView.addView(mLayoutCenter);

                mIconArea.removeView(mClock);
                mLayoutCenter.addView(mClock);

                super.beforeHookedMethod(param);
            }
        });

        //Context
        findAndHookMethod(CLASS_STATUSBAR, classLoader, "makeStatusBarView", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                context = (Context) getObjectField(param.thisObject,"mContext");
                super.beforeHookedMethod(param);
            }
        });

        //Handle Visibility
        findAndHookMethod(CLASS_COLLAPSED_SB_FRAGMENT, classLoader,
                "hideSystemIconArea", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (mLayoutCenter != null) {
                            mLayoutCenter.setVisibility(View.GONE);
                        }
                    }
                });
        findAndHookMethod(CLASS_COLLAPSED_SB_FRAGMENT, classLoader,
                "showSystemIconArea", boolean.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (mLayoutCenter != null) {
                            mLayoutCenter.setVisibility(View.VISIBLE);
                        }
                    }
                });

        //Clock Customizations (Example : 23sat7:30)
        findAndHookMethod("com.android.systemui.statusbar.policy.Clock", classLoader, "updateClock", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                XposedBridge.log("Panni : updateClock hooked");
                mClock = (TextView) param.thisObject;
                String customTime = new SimpleDateFormat("ddEEE h:m",Locale.ENGLISH).format(new Date()).toLowerCase();
                SpannableStringBuilder customTimeBuilder = new SpannableStringBuilder(customTime);
                customTimeBuilder.setSpan(new RelativeSizeSpan(0.7f),0,5/* Example 23sat, count is 5*/, Spanned.SPAN_EXCLUSIVE_INCLUSIVE);
                mClock.setText(customTimeBuilder);
                mClock.setTextSize(12);
                mClock.setGravity(Gravity.CENTER);
                mClock.setPadding(4,0,0,0);
            }
        });

        // Adjust notification icon area for center clock
        findAndHookMethod(CLASS_NOTIF_ICON_CONTAINER, classLoader,"getActualWidth", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (mLayoutCenter != null && mLayoutCenter.getChildCount() > 0 &&
                                mLayoutCenter.getChildAt(0).getVisibility() == View.VISIBLE &&
                                getBooleanField(param.thisObject, "mShowAllIcons"))
                        {
                            int width = Math.round(mLayoutCenter.getWidth()/2f -
                                    mLayoutCenter.getChildAt(0).getWidth()/2f) - 4;
                                param.setResult(width);
                        }
                    }
                });
    }
}