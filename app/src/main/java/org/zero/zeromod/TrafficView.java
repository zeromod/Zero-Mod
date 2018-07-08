package org.zero.zeromod;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.icu.text.DecimalFormat;
import android.net.TrafficStats;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class TrafficView extends android.support.v7.widget.AppCompatTextView {

    private static boolean justLaunched = true;
    private long totalTxBytes;
    private long totalRxBytes;
    private long lastUpdateTime;
    private static final int TRANSMIT = 0;
    private static final int RECEIVE = 1;

    public TrafficView(final Context context) {
        super(context,null,0);

            if (justLaunched) {
                //get the values for the first time
                lastUpdateTime = SystemClock.elapsedRealtime();
                totalTxBytes = getTotalBytes(TRANSMIT);
                totalRxBytes = getTotalBytes(RECEIVE);

                //don't get the values again
                justLaunched = false;
            }
            mTrafficHandler.sendEmptyMessage(0);
    }
    final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mTrafficHandler.sendEmptyMessage(0);
        }
    };

    @SuppressLint("HandlerLeak")
    Handler mTrafficHandler = new Handler()
    {
        @Override
        public void handleMessage(Message msg) {
        long lastUpdateTimeNew = SystemClock.elapsedRealtime();
        long downloadSpeed;
        long uploadSpeed;
        long elapsedTime = lastUpdateTimeNew - lastUpdateTime;
        long totalTxBytesNew = getTotalBytes(TRANSMIT);
        long totalRxBytesNew = getTotalBytes(RECEIVE);

        if (elapsedTime == 0) {
            uploadSpeed = 0;
            downloadSpeed = 0;
        } else {
            uploadSpeed = ((totalTxBytesNew - totalTxBytes) * 1000) / elapsedTime;
            downloadSpeed = ((totalRxBytesNew - totalRxBytes) * 1000) / elapsedTime;
        }

        totalTxBytes = totalTxBytesNew;
        totalRxBytes = totalRxBytesNew;
        lastUpdateTime = lastUpdateTimeNew;

        String strUploadValue = createText(uploadSpeed);
        String strDownloadValue = createText(downloadSpeed);

        String delimiter = "\n";
        String strTrafficValue = strUploadValue + delimiter + strDownloadValue;
        setText(strTrafficValue);
        setTypeface(null, Typeface.BOLD);

        removeCallbacks(mRunnable);
        postDelayed(mRunnable,1000);
    }
    };

    private long getTotalBytes(int traffic_direction) {

        final boolean tx = (traffic_direction == TRANSMIT);
        long totalBytes = -9; // not -1 because it conflicts with TrafficStats.UNSUPPORTED
        BufferedReader br = null;
        BufferedReader br2 = null;

        try {
            br = new BufferedReader(new FileReader("/sys/class/net/lo/statistics/" + (tx ? "tx" : "rx") + "_bytes"));

            // reading both together to reduce delay in between as much as possible
            totalBytes = tx ? TrafficStats.getTotalTxBytes() : TrafficStats.getTotalRxBytes();
            String line = br.readLine();

            long loBytes = Long.parseLong(line);

            long tun0Bytes = 0;

            File tun0 = new File("/sys/class/net/tun0");
            if (tun0.exists()) {
                br2 = new BufferedReader(new FileReader("/sys/class/net/tun0/statistics/" + (tx ? "tx" : "rx") + "_bytes"));
                String line2 = br2.readLine();
                tun0Bytes = Long.parseLong(line2);
            }

            totalBytes = totalBytes - loBytes - tun0Bytes;

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (br2 != null) {
                try {
                    br2.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        if (totalBytes == -9) {
            totalBytes = tx ? TrafficStats.getTotalTxBytes() : TrafficStats.getTotalRxBytes();
        }

        return totalBytes;
    }

    private String createText(long transferSpeed){
        float unitFactor = 1000f;
        int tempPrefUnit;
        float megaTransferSpeed = ((float) transferSpeed) / (unitFactor * unitFactor);
        float kiloTransferSpeed = ((float) transferSpeed) / unitFactor;
        float transferValue;
        DecimalFormat transferDecimalFormat;

        if (megaTransferSpeed >= 1) {
            tempPrefUnit = 3;

        } else if (kiloTransferSpeed >= 1) {
            tempPrefUnit = 2;

        } else {
            tempPrefUnit = 1;
        }

        switch (tempPrefUnit) {
            case 3:
                transferValue = megaTransferSpeed;
                transferDecimalFormat =  new DecimalFormat(" ##0.0");
                break;
            case 2:
                transferValue = kiloTransferSpeed;
                transferDecimalFormat =  new DecimalFormat(" ##0");
                break;
            default:
            case 1:
                transferValue = transferSpeed;
                transferDecimalFormat =  new DecimalFormat(" ##0");
                break;
        }
        String strTransferValue;
        strTransferValue = transferDecimalFormat.format(transferValue);
        if (transferValue < 5)
            setVisibility(GONE);
        else setVisibility(VISIBLE);

        String factor = "K";
        if (tempPrefUnit == 3)
            factor = "M";
        else if (tempPrefUnit == 1)
            factor = "B";

        StringBuilder unit = new StringBuilder();
        unit.append(factor);
        return strTransferValue+unit;
    }
}

