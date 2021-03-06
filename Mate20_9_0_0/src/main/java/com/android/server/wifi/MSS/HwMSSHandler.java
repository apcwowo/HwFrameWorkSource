package com.android.server.wifi.MSS;

import android.annotation.SuppressLint;
import android.common.HwFrameworkFactory;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiInfo;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.SystemProperties;
import android.util.Log;
import com.android.server.wifi.HwArpVerifier;
import com.android.server.wifi.HwMSSHandlerManager;
import com.android.server.wifi.HwWifiCHRService;
import com.android.server.wifi.HwWifiCHRServiceImpl;
import com.android.server.wifi.MSS.HwMSSArbitrager.IHwMSSObserver;
import com.android.server.wifi.MSS.HwMSSArbitrager.MSSState;
import com.android.server.wifi.MSS.HwMSSArbitrager.MSS_TRIG_TYPE;
import com.android.server.wifi.WifiNative;
import com.android.server.wifi.WifiNative.TxPacketCounters;
import com.android.server.wifi.wificond.NativeMssResult;
import java.util.ArrayList;

@SuppressLint({"HandlerLeak"})
public class HwMSSHandler implements HwMSSHandlerManager, IHwMSSObserver {
    private static final int KOG_MODE_HT40 = 99;
    private static final int MAX_FREQ_24G = 2484;
    private static final int MIN_FREQ_24G = 2412;
    private static final int MSG_HT_40 = 99;
    private static final int MSS_CHECK_DISCONN_ERR = 3;
    private static final int MSS_CHECK_PING_ERR = 2;
    private static final int MSS_CHECK_STATE_ERR = 1;
    private static final int MSS_FORCE_TO_MIMO = 1;
    private static final int MSS_FORCE_TO_SISO = 3;
    private static final int MSS_MAX_SWITCH_OPERATION = 4;
    private static final int MSS_MIMO_CHAIN_NUM = 3;
    private static final int MSS_RSSI_SWITCH_COUNT = 5;
    private static final int MSS_RSSI_SWITCH_MIMO_TRESH_2G = -76;
    private static final int MSS_RSSI_SWITCH_MIMO_TRESH_5G = -76;
    private static final int MSS_RSSI_SWITCH_SISO_TRESH_2G = -65;
    private static final int MSS_RSSI_SWITCH_SISO_TRESH_5G = -65;
    private static final int MSS_SISO_CHAIN_NUM = 1;
    private static final int MSS_START = 0;
    private static final int MSS_TEMP_SWITCH_COUNT = 1;
    private static final int MSS_TEMP_SWITCH_SISO_TRESH = 100;
    private static final int MSS_TPUT_SWITCH_MIMO_COUNT = 3;
    private static final int MSS_TPUT_SWITCH_MIMO_TRESH_2G = 30;
    private static final int MSS_TPUT_SWITCH_MIMO_TRESH_5G = 90;
    private static final int MSS_TPUT_SWITCH_SISO_COUNT = 3;
    private static final int MSS_TPUT_SWITCH_SISO_TRESH_2G = 10;
    private static final int MSS_TPUT_SWITCH_SISO_TRESH_5G = 10;
    private static final int RESTORE_CURRENT_MSS_STATE = 2;
    public static final int SUPER_MODE = 4;
    private static final String TAG = "HwMSSHandler";
    private static final String THERMAL_TO_WIFI = "huawei.intent.action.THERMAL_TO_WIFI";
    private static final int WIFI_THERMAL_ACTION_COUNT = 3;
    private static final int WIFI_THERMAL_LEVEL1 = 1;
    private static final int WIFI_THERMAL_LEVEL5 = 5;
    private static final int WIFI_THERMAL_MIMO_TO_SISO = 1001;
    private static final int WIFI_THERMAL_MIMO_TO_SISO_INTERVAL = 3000;
    private static HwMSSHandler mInstance = null;
    private static int mRestoreCount = 0;
    private static int mRssiMIMOCount = 0;
    private static int mRssiSISOCount = 0;
    private static int mTputMIMOCount = 0;
    private static int mTputSISOCount = 0;
    private int MSS_STATE_RES_CYCLE = 20;
    private IHwMSSBlacklistMgr blackMgr = null;
    public ArrayList list;
    private boolean m2GHT40Enabled = false;
    private String mCellPhoneWIFIIface = "wlan0";
    private int mCellPhoneWIFIMode = -1;
    private int mCellPhoneWIFIOperation = -1;
    private Context mContext = null;
    private int mCurrentRssi = 0;
    private int mCurrentTemp = 0;
    private int mCurrentTput = 0;
    private Handler mHandler = null;
    private HisiMSSStateMachine mHisiMssStateMachine = null;
    private boolean mIs1103 = false;
    private int mIsDisconnectHappened = 0;
    private int mIsSuppCompleted = 0;
    private HwMSSBluetoothManager mMSSBluetoothManager = null;
    private int mMSSDirection = 0;
    private int mTXGood = 0;
    private int mTXbad = 0;
    private int mTempSISOCount = 0;
    private int mThermalLevel = -1;
    private int mThermalScene = -1;
    private int mThermalTemp = -1;
    private int mTriggerReason;
    private int mTxbad_Last = 0;
    private int mTxgood_Last = 0;
    private boolean mWiFiApMode = false;
    private boolean mWifiConnected = false;
    private boolean mWifiEnabled = false;
    private WifiInfo mWifiInfo = null;
    private WifiNative mWifiNative = null;
    private WifiStateReceiver mWifiStateReceiver = null;
    private HwMSSArbitrager mssArbi = null;
    private boolean mssIsHighTput = false;
    private HwWifiCHRService wcsm = null;

    private enum MSS_TRIG_ARBITRATE_TYPE {
        RSSI_TRIG,
        TPUT_TRIG,
        TEMP_TRIG
    }

    private class WifiStateReceiver extends BroadcastReceiver {
        private WifiStateReceiver() {
        }

        /* synthetic */ WifiStateReceiver(HwMSSHandler x0, AnonymousClass1 x1) {
            this();
        }

        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                String str;
                int state;
                String action = intent.getAction();
                if ("android.net.wifi.supplicant.STATE_CHANGE".equals(action)) {
                    SupplicantState state2 = (SupplicantState) intent.getParcelableExtra("newState");
                    if (state2 != null) {
                        String str2 = HwMSSHandler.TAG;
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append("WifiStateReceiver:");
                        stringBuilder.append(action);
                        stringBuilder.append(" SupplicantState:");
                        stringBuilder.append(state2);
                        Log.d(str2, stringBuilder.toString());
                    }
                    if (state2 == SupplicantState.DISCONNECTED) {
                        HwMSSHandler.this.mWifiConnected = false;
                        HwMSSHandler.this.setWifiDisconnectedMSSState();
                    }
                }
                if ("android.net.wifi.STATE_CHANGE".equals(action)) {
                    NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra("networkInfo");
                    if (networkInfo != null) {
                        if (networkInfo.getDetailedState() == DetailedState.CONNECTED) {
                            if (HwMSSHandler.this.mIs1103) {
                                HwMSSHandler.this.mHisiMssStateMachine.sendMessage(10);
                            }
                            HwMSSHandler.this.mWifiConnected = true;
                            HwMSSHandler.this.mHandler.sendMessage(HwMSSHandler.this.mHandler.obtainMessage(2, Integer.valueOf(0)));
                        } else if (networkInfo.getDetailedState() == DetailedState.DISCONNECTED && HwMSSHandler.this.mIs1103) {
                            HwMSSHandler.this.mHisiMssStateMachine.sendMessage(11);
                        }
                        HwMSSHandler.this.clearMssCount();
                    } else {
                        return;
                    }
                }
                if ("android.net.wifi.p2p.CONNECTION_STATE_CHANGE".equals(action)) {
                    str = HwMSSHandler.TAG;
                    StringBuilder stringBuilder2 = new StringBuilder();
                    stringBuilder2.append("WifiStateReceiver:");
                    stringBuilder2.append(action);
                    Log.d(str, stringBuilder2.toString());
                    HwMSSHandler.this.mHandler.sendMessage(HwMSSHandler.this.mHandler.obtainMessage(2, Integer.valueOf(0)));
                } else if ("android.net.wifi.WIFI_STATE_CHANGED".equals(action)) {
                    state = intent.getIntExtra("wifi_state", 4);
                    if (3 == state) {
                        HwMSSHandler.this.mWifiEnabled = true;
                        HwMSSHandler.this.m2GHT40Enabled = false;
                        if (HwMSSHandler.this.mssArbi.matchHT40List()) {
                            HwMSSHandler.this.mHandler.sendMessage(HwMSSHandler.this.mHandler.obtainMessage(99, Integer.valueOf(1)));
                        }
                        if (HwMSSHandler.this.mIs1103) {
                            HwMSSHandler.this.mHisiMssStateMachine.sendMessage(12);
                        }
                    } else if (1 == state) {
                        HwMSSHandler.this.mWifiEnabled = false;
                        if (HwMSSHandler.this.mIs1103) {
                            HwMSSHandler.this.mHisiMssStateMachine.sendMessage(13);
                        }
                    } else {
                        HwMSSHandler.this.mWifiEnabled = false;
                    }
                }
                if (HwMSSHandler.THERMAL_TO_WIFI.equals(action)) {
                    HwMSSHandler.this.mThermalLevel = intent.getIntExtra("level", -1);
                    HwMSSHandler.this.mThermalTemp = intent.getIntExtra("temp", -1);
                    HwMSSHandler.this.mThermalScene = intent.getIntExtra("scene", -1);
                    str = HwMSSHandler.TAG;
                    StringBuilder stringBuilder3 = new StringBuilder();
                    stringBuilder3.append("receive thermal brocast temperature is:");
                    stringBuilder3.append(HwMSSHandler.this.mThermalTemp);
                    stringBuilder3.append(" ,level is:");
                    stringBuilder3.append(HwMSSHandler.this.mThermalLevel);
                    stringBuilder3.append(" ,Scene is:");
                    stringBuilder3.append(HwMSSHandler.this.mThermalScene);
                    Log.d(str, stringBuilder3.toString());
                    if (HwMSSHandler.this.mThermalLevel == 0) {
                        HwMSSHandler.this.clearTempCount(true);
                    } else if (HwMSSHandler.this.mThermalLevel >= 1 && HwMSSHandler.this.mThermalLevel <= 5 && !HwMSSHandler.this.mHandler.hasMessages(HwMSSHandler.WIFI_THERMAL_MIMO_TO_SISO)) {
                        HwMSSHandler.this.mHandler.sendMessage(HwMSSHandler.this.mHandler.obtainMessage(HwMSSHandler.WIFI_THERMAL_MIMO_TO_SISO, Integer.valueOf(0)));
                    }
                }
                if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(action)) {
                    state = intent.getIntExtra("wifi_state", 11);
                    if (state == 11) {
                        HwMSSHandler.this.mWiFiApMode = false;
                        HwMSSHandler.this.mssArbi.setSisoFixFlag(false);
                    } else if (state == 13) {
                        HwMSSHandler.this.mWiFiApMode = true;
                    }
                }
            }
        }
    }

    public static synchronized HwMSSHandlerManager getDefault(Context cxt, WifiNative wifinav, WifiInfo wifiinfo) {
        HwMSSHandler hwMSSHandler;
        synchronized (HwMSSHandler.class) {
            if (mInstance == null) {
                mInstance = new HwMSSHandler(cxt, wifinav, wifiinfo);
            }
            hwMSSHandler = mInstance;
        }
        return hwMSSHandler;
    }

    public static synchronized HwMSSHandler getInstance() {
        synchronized (HwMSSHandler.class) {
            if (mInstance == null) {
                return null;
            }
            HwMSSHandler hwMSSHandler = mInstance;
            return hwMSSHandler;
        }
    }

    private boolean isRssiBelowTwoCells() {
        int freq = this.mWifiInfo.getFrequency();
        if (freq < MIN_FREQ_24G || freq > MAX_FREQ_24G) {
            if (freq > MAX_FREQ_24G && this.mCurrentRssi <= -76) {
                return true;
            }
        } else if (this.mCurrentRssi <= -76) {
            return true;
        }
        return false;
    }

    private boolean isRssiFourCells() {
        int freq = this.mWifiInfo.getFrequency();
        if (freq < MIN_FREQ_24G || freq > MAX_FREQ_24G) {
            if (freq > MAX_FREQ_24G && this.mCurrentRssi >= -65) {
                return true;
            }
        } else if (this.mCurrentRssi >= -65) {
            return true;
        }
        return false;
    }

    private HwMSSHandler(Context cxt, WifiNative wifinav, WifiInfo wifiinfo) {
        this.mContext = cxt;
        this.mWifiNative = wifinav;
        this.mWifiInfo = wifiinfo;
        this.mssArbi = HwMSSArbitrager.getInstance(this.mContext);
        this.mIs1103 = HwMSSUtils.is1103();
        if (this.mIs1103) {
            this.MSS_STATE_RES_CYCLE = 120;
            HwMSSUtils.setAllowSwitch(false);
            this.mHisiMssStateMachine = HisiMSSStateMachine.createHisiMSSStateMachine(this, cxt, wifinav, wifiinfo);
        } else {
            this.blackMgr = HwMSSBlackListManager.getInstance(this.mContext);
        }
        this.wcsm = HwWifiCHRServiceImpl.getInstance();
        HwMSSHandlerInit();
        mssCheckInit(this.mContext);
        this.mssArbi.registerMSSObserver(this);
        if (SystemProperties.getInt("ro.config.hw_wifi_btc_mss_en", 0) == 1) {
            this.mMSSBluetoothManager = HwMSSBluetoothManager.getInstance(cxt);
            this.mMSSBluetoothManager.init(wifinav, wifiinfo);
        }
    }

    private void HwMSSHandlerInit() {
        HandlerThread handlerThread = new HandlerThread("mss_handler_thread");
        handlerThread.start();
        this.mHandler = new Handler(handlerThread.getLooper()) {
            public void handleMessage(Message msg) {
                if (HwMSSHandler.this.mIs1103) {
                    handleMessageForHisi(msg);
                } else {
                    handleMessageForBrcm(msg);
                }
                super.handleMessage(msg);
            }

            private void handleMessageForBrcm(Message msg) {
                int i = msg.what;
                if (i == 99) {
                    HwMSSHandler.this.enable2GHT40Band();
                } else if (i != HwMSSHandler.WIFI_THERMAL_MIMO_TO_SISO) {
                    switch (i) {
                        case 0:
                            if (HwMSSHandler.this.doMssSwitch(msg.arg1)) {
                                HwMSSHandler.this.mTriggerReason = msg.arg2;
                                HwMSSHandler.this.handleMssResultForBrcm(HwMSSHandler.this.mssResultCheck());
                                return;
                            }
                            return;
                        case 1:
                            HwMSSHandler.this.mssArbi.setSisoFixFlag(false);
                            HwMSSHandler.this.mWifiNative.hwABSSoftHandover(2);
                            HwMSSHandler.this.mssArbi.setMSSCurrentState(MSSState.MSSMIMO);
                            HwMSSHandler.this.clearMssCount();
                            return;
                        case 2:
                            HwMSSHandler.this.restoreCurrentChainState();
                            return;
                        case 3:
                            i = HwMSSHandler.this.setWifiAntImpl(HwMSSHandler.this.mCellPhoneWIFIIface, HwMSSHandler.this.mCellPhoneWIFIMode, HwMSSHandler.this.mCellPhoneWIFIOperation);
                            String str = HwMSSHandler.TAG;
                            StringBuilder stringBuilder = new StringBuilder();
                            stringBuilder.append("setWifiAntImpl result is ");
                            stringBuilder.append(i);
                            Log.d(str, stringBuilder.toString());
                            HwMSSHandler.this.clearMssCount();
                            return;
                        default:
                            return;
                    }
                } else if (HwMSSHandler.this.mThermalLevel >= 1 && HwMSSHandler.this.mThermalLevel <= 5) {
                    HwMSSHandler.this.mTempSISOCount = HwMSSHandler.this.mTempSISOCount + 1;
                    if (HwMSSHandler.this.mTempSISOCount == 3) {
                        HwMSSHandler.this.tempChangeWiFiState(HwMSSHandler.WIFI_THERMAL_MIMO_TO_SISO);
                        HwMSSHandler.this.clearTempCount(false);
                    }
                    if (!HwMSSHandler.this.mHandler.hasMessages(HwMSSHandler.WIFI_THERMAL_MIMO_TO_SISO)) {
                        sendEmptyMessageDelayed(HwMSSHandler.WIFI_THERMAL_MIMO_TO_SISO, 3000);
                    }
                }
            }

            private void handleMessageForHisi(Message msg) {
                int i = msg.what;
                if (i == 99) {
                    return;
                }
                if (i != HwMSSHandler.WIFI_THERMAL_MIMO_TO_SISO) {
                    switch (i) {
                        case 0:
                            if (HwMSSHandler.this.doMssSwitchForHisi(msg.arg1, false)) {
                                HwMSSHandler.this.mTriggerReason = msg.arg2;
                                return;
                            }
                            return;
                        case 1:
                            HwMSSHandler.this.mssArbi.setSisoFixFlag(false);
                            HwMSSHandler.this.doMssSwitchForHisi(2, true);
                            return;
                        case 2:
                            HwMSSHandler.this.restoreCurrentChainStateForHisi();
                            return;
                        case 3:
                            HwMSSHandler.this.doMssSwitchForHisi(HwMSSHandler.this.mCellPhoneWIFIOperation, true);
                            return;
                        default:
                            return;
                    }
                } else if (HwMSSHandler.this.mThermalLevel >= 1 && HwMSSHandler.this.mThermalLevel <= 5) {
                    HwMSSHandler.this.mTempSISOCount = HwMSSHandler.this.mTempSISOCount + 1;
                    if (HwMSSHandler.this.mTempSISOCount == 3) {
                        HwMSSHandler.this.tempChangeWiFiState(HwMSSHandler.WIFI_THERMAL_MIMO_TO_SISO);
                        HwMSSHandler.this.clearTempCount(false);
                    }
                    if (!HwMSSHandler.this.mHandler.hasMessages(HwMSSHandler.WIFI_THERMAL_MIMO_TO_SISO)) {
                        sendEmptyMessageDelayed(HwMSSHandler.WIFI_THERMAL_MIMO_TO_SISO, 3000);
                    }
                }
            }
        };
    }

    private boolean mssPreArbitrate(MSS_TRIG_ARBITRATE_TYPE type, int direction) {
        int tputSwitchMimoTresh = getTputSwitchMimoTresh();
        switch (type) {
            case RSSI_TRIG:
                if (1 == direction && ((tputSwitchMimoTresh != 0 && this.mCurrentTput >= tputSwitchMimoTresh) || this.mssIsHighTput)) {
                    Log.d(TAG, "throughput is over the threshold, do not switch");
                    return false;
                }
            case TPUT_TRIG:
                if (1 == direction && !isRssiFourCells()) {
                    Log.d(TAG, "rssi is below four sells, do not switch");
                    return false;
                } else if (2 == direction && this.mThermalLevel >= 1 && this.mThermalLevel <= 5) {
                    Log.d(TAG, "temperature is over the threshold, do not switch");
                    return false;
                }
                break;
            case TEMP_TRIG:
                if (1 == direction && !isRssiFourCells()) {
                    Log.d(TAG, "rssi is below four cells, do not switch");
                    return false;
                }
        }
        return true;
    }

    private boolean mssSwitchSupportCheck() {
        if (this.mssArbi.isMSSSwitchBandSupport()) {
            return true;
        }
        return false;
    }

    private int getTputSwitchMimoTresh() {
        int freq = this.mWifiInfo.getFrequency();
        if (freq >= MIN_FREQ_24G && freq <= MAX_FREQ_24G) {
            return 30;
        }
        if (freq > MAX_FREQ_24G) {
            return MSS_TPUT_SWITCH_MIMO_TRESH_5G;
        }
        return 0;
    }

    private int getTputSwitchSisoTresh() {
        int freq = this.mWifiInfo.getFrequency();
        if ((freq < MIN_FREQ_24G || freq > MAX_FREQ_24G) && freq <= MAX_FREQ_24G) {
            return 0;
        }
        return 10;
    }

    public void mssSwitchCheck(int rssi) {
        if (mssSwitchSupportCheck() && allowMSSSwitch()) {
            int direction;
            this.mCurrentRssi = rssi;
            mRestoreCount++;
            if (mRestoreCount == this.MSS_STATE_RES_CYCLE) {
                this.mHandler.sendMessage(this.mHandler.obtainMessage(2, Integer.valueOf(0)));
                mRestoreCount = 0;
            }
            if (isRssiFourCells() && this.mssArbi.getMSSCurrentState() == MSSState.MSSMIMO) {
                mRssiSISOCount++;
                mRssiMIMOCount = 0;
            } else if (isRssiBelowTwoCells() && this.mssArbi.getMSSCurrentState() == MSSState.MSSSISO) {
                mRssiMIMOCount++;
                mRssiSISOCount = 0;
            } else {
                mRssiSISOCount = 0;
                mRssiMIMOCount = 0;
            }
            String str = TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("doMssSwitch rssi:");
            stringBuilder.append(rssi);
            stringBuilder.append(" CurrentChainState:");
            stringBuilder.append(this.mssArbi.getMSSCurrentState());
            stringBuilder.append(" SISOCount:");
            stringBuilder.append(mRssiSISOCount);
            stringBuilder.append(" MIMOCount:");
            stringBuilder.append(mRssiMIMOCount);
            Log.d(str, stringBuilder.toString());
            if (mRssiMIMOCount >= 5 && this.mssArbi.getMSSCurrentState() == MSSState.MSSSISO) {
                direction = 2;
            } else if (mRssiSISOCount >= 5 && this.mssArbi.getMSSCurrentState() == MSSState.MSSMIMO) {
                direction = 1;
                if (!mssPreArbitrate(MSS_TRIG_ARBITRATE_TYPE.RSSI_TRIG, 1)) {
                    return;
                }
            } else {
                return;
            }
            this.mHandler.sendMessage(this.mHandler.obtainMessage(0, direction, MSS_TRIG_ARBITRATE_TYPE.RSSI_TRIG.ordinal()));
        }
    }

    public void mssSwitchCheckTPut(int tput) {
        if (mssSwitchSupportCheck() && allowMSSSwitch()) {
            this.mCurrentTput = tput;
            int tputSwitchToMimoTresh = getTputSwitchMimoTresh();
            int tputSwitchToSisoTresh = getTputSwitchSisoTresh();
            if (tputSwitchToMimoTresh != 0 && tputSwitchToSisoTresh != 0) {
                int direction;
                if (tput >= tputSwitchToMimoTresh) {
                    mTputMIMOCount++;
                    mTputSISOCount = 0;
                } else if (tput <= tputSwitchToSisoTresh) {
                    mTputSISOCount++;
                    mTputMIMOCount = 0;
                } else {
                    mTputSISOCount = 0;
                    mTputMIMOCount = 0;
                }
                String str = TAG;
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("doMssSwitch tput:");
                stringBuilder.append(tput);
                stringBuilder.append(" CurrentChainState:");
                stringBuilder.append(this.mssArbi.getMSSCurrentState());
                stringBuilder.append(" SISOCount:");
                stringBuilder.append(mTputSISOCount);
                stringBuilder.append(" MIMOCount:");
                stringBuilder.append(mTputMIMOCount);
                stringBuilder.append(" mssIsHighTput:");
                stringBuilder.append(this.mssIsHighTput);
                Log.d(str, stringBuilder.toString());
                if (mTputMIMOCount >= 3) {
                    direction = 2;
                    this.mssIsHighTput = true;
                    if (this.mssArbi.getMSSCurrentState() == MSSState.MSSMIMO || !mssPreArbitrate(MSS_TRIG_ARBITRATE_TYPE.TPUT_TRIG, 2)) {
                        mTputSISOCount = 0;
                        mTputMIMOCount = 0;
                        return;
                    }
                } else if (mTputSISOCount >= 3) {
                    direction = 1;
                    this.mssIsHighTput = false;
                    if (this.mssArbi.getMSSCurrentState() == MSSState.MSSSISO || !mssPreArbitrate(MSS_TRIG_ARBITRATE_TYPE.TPUT_TRIG, 1)) {
                        mTputSISOCount = 0;
                        mTputMIMOCount = 0;
                        return;
                    }
                } else {
                    return;
                }
                this.mHandler.sendMessage(this.mHandler.obtainMessage(0, direction, MSS_TRIG_ARBITRATE_TYPE.TPUT_TRIG.ordinal()));
            }
        }
    }

    private void tempChangeWiFiState(int action) {
        if (WIFI_THERMAL_MIMO_TO_SISO == action && this.mssArbi.isWiFiConnected()) {
            if (!mssSwitchSupportCheck() || this.mssArbi.getMSSCurrentState() == MSSState.MSSSISO || !allowMSSSwitch()) {
                Log.d(TAG, "NOT MIMO or NOT support this switch return");
            } else if (mssPreArbitrate(MSS_TRIG_ARBITRATE_TYPE.TEMP_TRIG, 1)) {
                Log.d(TAG, "temperature is over the threshold, begin to switch,direction is MIMO --> SISO");
                this.mHandler.sendMessage(this.mHandler.obtainMessage(0, 1, MSS_TRIG_ARBITRATE_TYPE.TEMP_TRIG.ordinal()));
            } else {
                Log.d(TAG, "rssi do not allow this switch");
            }
        }
    }

    private void clearTempCount(boolean isNeedRemoveMsg) {
        this.mTempSISOCount = 0;
        if (isNeedRemoveMsg) {
            this.mHandler.removeMessages(WIFI_THERMAL_MIMO_TO_SISO);
        }
    }

    private void restoreCurrentChainState() {
        Log.d(TAG, "Start restoreCurrentChainState");
        int mssState = this.mWifiNative.getWifiAnt("wlan0", 0);
        if (mssState == -1 || changeAntReturnValueToMssState(mssState) == MSSState.MSSUNKNOWN) {
            Log.d(TAG, "restore current Chain State wrong!");
            return;
        }
        this.mssArbi.setMSSCurrentState(changeAntReturnValueToMssState(mssState));
        clearMssCount();
        if (changeAntReturnValueToMssState(mssState) == MSSState.MSSMIMO) {
            this.mssArbi.setSisoFixFlag(false);
        }
    }

    private void mssCheckInit(Context context) {
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.net.wifi.supplicant.STATE_CHANGE");
        filter.addAction("android.net.wifi.WIFI_STATE_CHANGED");
        filter.addAction("android.net.wifi.p2p.STATE_CHANGED");
        filter.addAction("android.net.wifi.p2p.CONNECTION_STATE_CHANGE");
        filter.addAction("android.net.wifi.p2p.DISCOVERY_STATE_CHANGE");
        filter.addAction("android.net.wifi.p2p.PEERS_CHANGED");
        filter.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
        filter.addAction(THERMAL_TO_WIFI);
        filter.addAction("android.net.wifi.STATE_CHANGE");
        this.mWifiStateReceiver = new WifiStateReceiver(this, null);
        context.registerReceiver(this.mWifiStateReceiver, filter);
    }

    private void clearMssCount() {
        mRssiSISOCount = 0;
        mRssiMIMOCount = 0;
        mTputSISOCount = 0;
        mTputMIMOCount = 0;
    }

    private MSSState changeAntReturnValueToMssState(int check) {
        if (check == 1) {
            return MSSState.MSSMIMO;
        }
        if (check == 0) {
            return MSSState.MSSSISO;
        }
        return MSSState.MSSUNKNOWN;
    }

    public boolean doMssSwitch(int direction) {
        if (this.m2GHT40Enabled && direction == 1) {
            Log.d(TAG, "HT40 Enabled, not allowed to swtich");
            return false;
        } else if (!this.mssArbi.isMSSAllowed(direction, this.mWifiInfo.getFrequency(), MSS_TRIG_TYPE.COMMON_TRIG)) {
            Log.d(TAG, "mss is not allowed!");
            clearMssCount();
            return false;
        } else if (this.mWifiNative.hwABSSoftHandover(direction)) {
            if (direction == 2) {
                this.mssArbi.setMSSCurrentState(MSSState.MSSMIMO);
            } else {
                this.mssArbi.setMSSCurrentState(MSSState.MSSSISO);
            }
            this.mMSSDirection = direction;
            clearMssCount();
            return true;
        } else {
            String str = TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("hwABSSoftHandover fail,direction:");
            stringBuilder.append(direction);
            Log.d(str, stringBuilder.toString());
            clearMssCount();
            return false;
        }
    }

    public int mssResultCheck() {
        WifiNative wifiNative = this.mWifiNative;
        Log.d(TAG, "mssResultCheck");
        if (1 == SystemProperties.getInt("runtime.hwmss.errtest", 0)) {
            Log.d(TAG, "mssResultCheck:error for test");
            return 1;
        }
        HwArpVerifier arpVerifier = HwArpVerifier.getDefault();
        this.mIsDisconnectHappened = 0;
        if (!this.mWiFiApMode) {
            fetchPktcntNative();
            if (!arpVerifier.mssGatewayVerifier()) {
                Log.d(TAG, "mssGatewayVerifier fail");
                fetchPktcntNative();
                return 2;
            }
        }
        int mssState = this.mWifiNative.getWifiAnt("wlan0", 0);
        if (mssState == -1 || changeAntReturnValueToMssState(mssState) != this.mssArbi.getMSSCurrentState()) {
            Log.d(TAG, "mssResultCheck:Current Chain State error");
            return 1;
        } else if (this.mWiFiApMode || this.mIsDisconnectHappened != 1) {
            return 0;
        } else {
            Log.d(TAG, "mIsDisconnectHappened fail");
            this.mIsDisconnectHappened = 0;
            return 3;
        }
    }

    public void mssRecoverWifiLink() {
        this.mWifiNative.hwABSSoftHandover(2);
        this.mssArbi.setMSSCurrentState(MSSState.MSSMIMO);
        clearMssCount();
        this.mWifiNative.reassociate(this.mWifiNative.getClientInterfaceName());
    }

    public void onMSSSwitchRequest(int direction) {
        if (allowMSSSwitch() && direction == 2 && MSSState.MSSSISO == this.mssArbi.getMSSCurrentState() && !this.mssArbi.getSisoFixFlag()) {
            Log.d(TAG, "onMSSSwitchRequest MSS_SISO_TO_MIMO");
            this.mHandler.sendMessage(this.mHandler.obtainMessage(1, Integer.valueOf(0)));
        }
    }

    private void fetchPktcntNative() {
        if (this.mWifiNative != null) {
            TxPacketCounters counters = this.mWifiNative.getTxPacketCounters(this.mWifiNative.getClientInterfaceName());
            if (counters != null) {
                int tx_Good = counters.txSucceeded;
                int tx_bad = counters.txFailed;
                this.mTXGood = tx_Good - this.mTxgood_Last;
                this.mTxgood_Last = tx_Good;
                this.mTXbad = tx_bad - this.mTxbad_Last;
                this.mTxbad_Last = tx_bad;
            }
        }
    }

    private ArrayList getParamList() {
        this.list = new ArrayList();
        this.list.add(Integer.valueOf(this.mTXGood));
        this.list.add(Integer.valueOf(this.mTXbad));
        this.list.add(Integer.valueOf(this.mThermalLevel));
        this.list.add(Integer.valueOf(this.mThermalTemp));
        this.list.add(Integer.valueOf(this.mThermalScene));
        this.list.add(Integer.valueOf(this.mTriggerReason));
        return this.list;
    }

    private int setWifiAntImpl(String iface, int mode, int operation) {
        String str = TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("setWifiAnt, interface, mode ,operation:");
        stringBuilder.append(iface);
        stringBuilder.append(",");
        stringBuilder.append(mode);
        stringBuilder.append(",");
        stringBuilder.append(operation);
        Log.d(str, stringBuilder.toString());
        if (!mssSwitchSupportCheck()) {
            Log.d(TAG, "setWifiAnt, mssSwitch not support");
            return -1;
        } else if (iface == null || iface.isEmpty()) {
            Log.d(TAG, "setWifiAnt, parameter iface error");
            return -1;
        } else if (operation > 4) {
            Log.d(TAG, "setWifiAnt, parameter operation error");
            return -1;
        } else if (operation == 2 && this.mssArbi.getMSSCurrentState() == MSSState.MSSMIMO) {
            Log.d(TAG, "setWifiAnt, MIMO state, no need to change to MIMO");
            return -1;
        } else if (!this.mssArbi.isMSSAllowed(operation, this.mWifiInfo.getFrequency(), MSS_TRIG_TYPE.CLONE_TRIG)) {
            Log.d(TAG, "mss is not allowed!");
            clearMssCount();
            return -1;
        } else if (this.mWifiNative.setWifiAnt(iface, mode, operation) == -1) {
            str = TAG;
            StringBuilder stringBuilder2 = new StringBuilder();
            stringBuilder2.append("setWifiAnt fail, operation:");
            stringBuilder2.append(operation);
            Log.d(str, stringBuilder2.toString());
            clearMssCount();
            return -1;
        } else {
            int reason;
            if (operation == 2) {
                this.mssArbi.setMSSCurrentState(MSSState.MSSMIMO);
            } else {
                this.mssArbi.setMSSCurrentState(MSSState.MSSSISO);
            }
            this.mMSSDirection = operation;
            clearMssCount();
            if (mssResultCheck() != 0) {
                mssRecoverWifiLink();
                this.wcsm.updateMSSCHR(this.mMSSDirection, this.mssArbi.getABSCurrentState().ordinal(), 0, getParamList());
                str = TAG;
                stringBuilder = new StringBuilder();
                stringBuilder.append("setWifiAnt fail, operation:");
                stringBuilder.append(0);
                Log.d(str, stringBuilder.toString());
                reason = -1;
            } else {
                this.wcsm.updateMSSCHR(this.mMSSDirection, this.mssArbi.getABSCurrentState().ordinal(), 0, getParamList());
                this.mssArbi.setSisoFixFlag(true);
                reason = 0;
            }
            Log.d(TAG, "setWifiAnt success");
            return reason;
        }
    }

    public void setWifiAnt(String iface, int mode, int operation) {
        Log.d(TAG, "before setWifiAnt sendmessage");
        if (this.mHandler != null) {
            Message msg = this.mHandler.obtainMessage();
            msg.what = 3;
            this.mCellPhoneWIFIIface = iface;
            this.mCellPhoneWIFIMode = mode;
            this.mCellPhoneWIFIOperation = operation;
            this.mHandler.sendMessage(msg);
            Log.d(TAG, "setWifiAnt sendmessage");
        }
    }

    public void notifyWifiDisconnected() {
        Log.d(TAG, "WLAN+ MSSWifiForceToMIMO success");
        setWifiDisconnectedMSSState();
    }

    private void setWifiDisconnectedMSSState() {
        this.mIsDisconnectHappened = 1;
        this.mssArbi.setSisoFixFlag(false);
        if (this.mssArbi.getMSSCurrentState() == MSSState.MSSSISO && !this.mssArbi.isP2PConnected() && !this.mWiFiApMode) {
            this.mHandler.sendMessage(this.mHandler.obtainMessage(1, Integer.valueOf(0)));
        }
    }

    private boolean isHuaweiAp() {
        String hwSsid = "\"Huawei-Employee\"";
        if (this.mWifiInfo.getSSID() == null || !this.mWifiInfo.getSSID().equals(hwSsid)) {
            return false;
        }
        return true;
    }

    private boolean isMobileAP() {
        if (this.mContext != null) {
            return HwFrameworkFactory.getHwInnerWifiManager().getHwMeteredHint(this.mContext);
        }
        return false;
    }

    private boolean allowMSSSwitch() {
        if (SystemProperties.getInt("ro.config.hw_wifi_btc_mss_en", 0) == 1) {
            return false;
        }
        return isHuaweiAp() || isMobileAP() || this.mssArbi.matchAllowMSSApkList();
    }

    public void onHT40Request() {
        this.mHandler.sendMessage(this.mHandler.obtainMessage(99, Integer.valueOf(0)));
    }

    public void enable2GHT40Band() {
        boolean isSupportHT40 = this.mssArbi.isSupportHT40();
        Log.d(TAG, "enable2GHT40Band enter");
        if (this.mWifiEnabled && isSupportHT40 && !this.m2GHT40Enabled) {
            this.m2GHT40Enabled = true;
            this.mWifiNative.gameKOGAdjustSpeed(0, 99);
            if (this.mWifiConnected) {
                Log.d(TAG, "should reassociate after enalbe ht40 for 2.4G");
                this.mWifiNative.reassociate(this.mWifiNative.getClientInterfaceName());
            }
            return;
        }
        String str = TAG;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ht40:");
        stringBuilder.append(this.m2GHT40Enabled);
        stringBuilder.append(",state:");
        stringBuilder.append(this.mWifiEnabled);
        stringBuilder.append(",support:");
        stringBuilder.append(isSupportHT40);
        Log.d(str, stringBuilder.toString());
    }

    private void handleMssResultForBrcm(int reason) {
        if (reason != 0) {
            if (this.blackMgr != null && isHuaweiAp()) {
                this.blackMgr.addToBlacklist(this.mWifiInfo.getSSID(), this.mWifiInfo.getBSSID(), reason);
            }
            mssRecoverWifiLink();
            this.wcsm.updateMSSCHR(this.mMSSDirection, this.mssArbi.getABSCurrentState().ordinal(), reason, getParamList());
            return;
        }
        this.wcsm.updateMSSCHR(this.mMSSDirection, this.mssArbi.getABSCurrentState().ordinal(), 0, getParamList());
    }

    private boolean doMssSwitchForHisi(int direction, boolean force) {
        if (this.mHisiMssStateMachine == null) {
            HwMSSUtils.loge(TAG, "mHisiMssStateMachine is null");
            return false;
        } else if (force) {
            this.mHisiMssStateMachine.doMssSwitch(direction);
            this.mMSSDirection = direction;
            clearMssCount();
            return true;
        } else if (this.mssArbi.isMSSAllowed(direction, this.mWifiInfo.getFrequency(), MSS_TRIG_TYPE.COMMON_TRIG)) {
            this.mHisiMssStateMachine.doMssSwitch(direction);
            this.mMSSDirection = direction;
            clearMssCount();
            return true;
        } else {
            Log.d(TAG, "mss is not allowed!");
            clearMssCount();
            return false;
        }
    }

    private void restoreCurrentChainStateForHisi() {
        HwMSSUtils.logd(TAG, "Start restoreCurrentChainState");
        this.mHisiMssStateMachine.sendMessage(5);
    }

    public void callbackSyncMssState(MSSState state) {
        this.mssArbi.setMSSCurrentState(state);
        if (state == MSSState.MSSMIMO) {
            this.mssArbi.setSisoFixFlag(false);
        }
        clearMssCount();
    }

    public void callbackReportCHR(NativeMssResult mssstru) {
        if (mssstru != null && mssstru.vapNum > (byte) 0 && this.wcsm != null) {
            if (mssstru.mssResult == (byte) 1) {
                HwMSSUtils.logd(TAG, "report chr: mss succ");
                this.wcsm.updateMSSCHR(this.mMSSDirection, this.mssArbi.getABSCurrentState().ordinal(), 0, getParamList());
                return;
            }
            String str = TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("report chr: mss fail, mode:");
            stringBuilder.append(mssstru.mssMode);
            HwMSSUtils.logd(str, stringBuilder.toString());
            this.wcsm.updateMSSCHR(this.mMSSDirection, this.mssArbi.getABSCurrentState().ordinal(), mssstru.mssMode, getParamList());
        }
    }

    public void onMssDrvEvent(NativeMssResult mssstru) {
        if (this.mHisiMssStateMachine != null && mssstru != null) {
            this.mHisiMssStateMachine.onMssDrvEvent(mssstru);
        }
    }
}
