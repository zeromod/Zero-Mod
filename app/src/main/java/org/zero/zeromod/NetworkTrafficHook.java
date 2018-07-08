package org.zero.zeromod;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.XResources;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import de.robv.android.xposed.IXposedHookInitPackageResources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_InitPackageResources;
import de.robv.android.xposed.callbacks.XC_LayoutInflated;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class NetworkTrafficHook implements IXposedHookInitPackageResources, IXposedHookLoadPackage {

    private Context context;
    private ViewGroup mStatusBarContents;
    private LinearLayout mLayoutTraffic;
    private TextView mTrafficView;
    private final String SYSTEM_UI = "com.android.systemui";

    @SuppressLint("HandlerLeak")
    @Override
    public void handleInitPackageResources(XC_InitPackageResources.InitPackageResourcesParam resparam) {

        if (!resparam.packageName.equals("com.android.systemui")) {
            return;
        }
        XResources res = resparam.res;

        res.hookLayout(SYSTEM_UI, "layout", "status_bar",
                new XC_LayoutInflated() {

                    @Override
                    public final void handleLayoutInflated(final LayoutInflatedParam liparam) {

                        FrameLayout root = (FrameLayout) liparam.view;
                        context = root.getContext();
                        TextView clock = root.findViewById(liparam.res.getIdentifier("clock", "id", SYSTEM_UI));
                        mStatusBarContents = root
                                .findViewById(liparam.res.getIdentifier("status_bar_contents", "id", SYSTEM_UI));

                        mLayoutTraffic = new LinearLayout(context);
                        mLayoutTraffic.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
                        mLayoutTraffic.setOrientation(LinearLayout.HORIZONTAL);
                        mLayoutTraffic.setWeightSum(1);

                        mTrafficView = new TrafficView(context);
                        mTrafficView.setLayoutParams(clock.getLayoutParams());
                        mTrafficView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                        mTrafficView.setTextSize(6);

                        mStatusBarContents.addView(mLayoutTraffic, 0);
                        mLayoutTraffic.addView(mTrafficView);

                    }
                });
    }

    /*
    Sync with Clock color.
    From Android M, DarkIconDispatcher is used to set dark/light colors.
    So clock.getCurrentTextColor() will not work.
    */
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.android.systemui"))
            return;

        final String RECT = "android.graphics.Rect";
        final String CLOCK = "com.android.systemui.statusbar.policy.Clock";
        final String DARK_ICON_DISPATCHER = "com.android.systemui.statusbar.policy.DarkIconDispatcher";

        XposedHelpers.findAndHookMethod(CLOCK, lpparam.classLoader, "onDarkChanged", RECT/*area*/, "float"/*darkIntensity*/, "int"/*tint*/, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                int color = (int) XposedHelpers.callStaticMethod(XposedHelpers.findClass(DARK_ICON_DISPATCHER,lpparam.classLoader),"getTint",param.args[0]/*Rect area*/,param.thisObject/*Clock*/,param.args[2]/*int tint*/);
                mTrafficView.setTextColor(color);
                super.afterHookedMethod(param);
            }
        });
    }
}
