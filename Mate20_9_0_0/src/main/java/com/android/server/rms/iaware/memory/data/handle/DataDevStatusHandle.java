package com.android.server.rms.iaware.memory.data.handle;

import android.os.Bundle;
import android.rms.iaware.AwareLog;
import com.android.server.rms.iaware.memory.data.content.AttrSegments;
import com.android.server.rms.iaware.memory.utils.MemoryConstant;

public class DataDevStatusHandle extends AbsDataHandle {
    private static final String TAG = "AwareMem_DevHandle";
    private static DataDevStatusHandle sDataHandle;

    public static DataDevStatusHandle getInstance() {
        DataDevStatusHandle dataDevStatusHandle;
        synchronized (DataDevStatusHandle.class) {
            if (sDataHandle == null) {
                sDataHandle = new DataDevStatusHandle();
            }
            dataDevStatusHandle = sDataHandle;
        }
        return dataDevStatusHandle;
    }

    public int reportData(long timestamp, int event, AttrSegments attrSegments) {
        if (event == 20011) {
            AwareLog.d(TAG, "dev status event screen on");
            this.mDMEServer.enable();
            Bundle extras = new Bundle();
            extras.putString("appName", "ScreenON");
            this.mDMEServer.execute(MemoryConstant.MEM_SCENE_DEFAULT, extras, event, timestamp);
        } else if (event != 90011) {
            AwareLog.w(TAG, "Dev Status event invalid");
        } else {
            AwareLog.d(TAG, "dev status event screen off");
            this.mDMEServer.disable();
        }
        return 0;
    }

    private DataDevStatusHandle() {
    }
}
