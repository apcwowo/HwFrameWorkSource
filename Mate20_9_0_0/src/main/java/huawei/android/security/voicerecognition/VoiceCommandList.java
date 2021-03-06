package huawei.android.security.voicerecognition;

import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.util.Slog;
import java.util.ArrayList;
import java.util.List;

public class VoiceCommandList implements Parcelable {
    public static final Creator<VoiceCommandList> CREATOR = new Creator<VoiceCommandList>() {
        public VoiceCommandList createFromParcel(Parcel in) {
            return new VoiceCommandList(in, null);
        }

        public VoiceCommandList[] newArray(int size) {
            return new VoiceCommandList[size];
        }
    };
    private static final int INDEX_ALGO_ID = 2;
    private static final int INDEX_HEADSET_ID = 1;
    private static final String TAG = VoiceCommandList.class.getSimpleName();
    private List<VoiceConfiguration> mConfigList;
    private int mIdForUnlock;
    private String mVersion;

    public static class VoiceConfiguration implements Parcelable {
        public static final Creator<VoiceConfiguration> CREATOR = new Creator<VoiceConfiguration>() {
            public VoiceConfiguration createFromParcel(Parcel in) {
                return new VoiceConfiguration(in, null);
            }

            public VoiceConfiguration[] newArray(int size) {
                return new VoiceConfiguration[size];
            }
        };
        public int mAlgoId;
        public int mCallFlag;
        public String mCommandString;
        public int mHeadsetId;
        public String mVoiceTag;

        /* synthetic */ VoiceConfiguration(Parcel x0, AnonymousClass1 x1) {
            this(x0);
        }

        public VoiceConfiguration(int headsetId, String commandStr, int algoId, String voiceTag, int callflag) {
            this.mCommandString = null;
            this.mVoiceTag = null;
            this.mCallFlag = 0;
            this.mHeadsetId = headsetId;
            this.mCommandString = commandStr;
            this.mAlgoId = algoId;
            this.mVoiceTag = voiceTag;
            this.mCallFlag = callflag;
        }

        private VoiceConfiguration(Parcel in) {
            this.mCommandString = null;
            this.mVoiceTag = null;
            this.mCallFlag = 0;
            this.mHeadsetId = in.readInt();
            this.mCommandString = in.readString();
            this.mAlgoId = in.readInt();
            this.mVoiceTag = in.readString();
            this.mCallFlag = in.readInt();
        }

        public int describeContents() {
            return 0;
        }

        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(this.mHeadsetId);
            dest.writeString(this.mCommandString);
            dest.writeInt(this.mAlgoId);
            dest.writeString(this.mVoiceTag);
            dest.writeInt(this.mCallFlag);
        }

        public String toString() {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("VoiceConfiguration{mHeadsetId=");
            stringBuilder.append(this.mHeadsetId);
            stringBuilder.append(", mCommandString=");
            stringBuilder.append(this.mCommandString);
            stringBuilder.append(", mAlgoId=");
            stringBuilder.append(this.mAlgoId);
            stringBuilder.append(", mVoiceTag=");
            stringBuilder.append(this.mVoiceTag);
            stringBuilder.append(", mCallFlag=");
            stringBuilder.append(this.mCallFlag);
            stringBuilder.append("}");
            return stringBuilder.toString();
        }

        public boolean isHeadsetIdEquals(int headsetId) {
            if (this.mHeadsetId == headsetId) {
                return true;
            }
            return false;
        }

        public boolean isAlgoIdEquals(int algoId) {
            if (this.mAlgoId == algoId) {
                return true;
            }
            return false;
        }
    }

    public VoiceCommandList(String version, int idForUnlock) {
        this.mVersion = "";
        this.mIdForUnlock = -1;
        this.mConfigList = new ArrayList();
        this.mVersion = version;
        this.mIdForUnlock = idForUnlock;
    }

    public VoiceCommandList(String version, int idForLock, List<VoiceConfiguration> voiceList) {
        this.mVersion = "";
        this.mIdForUnlock = -1;
        this.mConfigList = new ArrayList();
        this.mVersion = version;
        this.mIdForUnlock = idForLock;
        this.mConfigList.addAll(voiceList);
    }

    private VoiceCommandList(Parcel in) {
        this.mVersion = "";
        this.mIdForUnlock = -1;
        this.mConfigList = new ArrayList();
        this.mVersion = in.readString();
        this.mIdForUnlock = in.readInt();
        this.mConfigList = in.readArrayList(VoiceConfiguration.class.getClassLoader());
    }

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeString(this.mVersion);
        out.writeInt(this.mIdForUnlock);
        out.writeList(this.mConfigList);
    }

    public String toString() {
        return "VoiceCommandList";
    }

    public int getListSize() {
        if (this.mConfigList != null) {
            return this.mConfigList.size();
        }
        return 0;
    }

    public List<VoiceConfiguration> getConfigList() {
        return this.mConfigList;
    }

    public String gatConfigVersion() {
        return this.mVersion;
    }

    public int getUnlockId() {
        return this.mIdForUnlock;
    }

    public void setVersion(String version) {
        this.mVersion = version;
    }

    public VoiceConfiguration getConfigurationByHeadsetId(int headsetId) {
        return getConfigurationCommon(1, headsetId);
    }

    public VoiceConfiguration getConfigurationByAlgoId(int algoId) {
        return getConfigurationCommon(2, algoId);
    }

    private VoiceConfiguration getConfigurationCommon(int index, int id) {
        int size = this.mConfigList.size();
        for (int i = 0; i < size; i++) {
            VoiceConfiguration config = (VoiceConfiguration) this.mConfigList.get(i);
            if (isIdEquals(config, index, id)) {
                return config;
            }
        }
        return null;
    }

    private boolean isIdEquals(VoiceConfiguration config, int index, int id) {
        if (config == null) {
            return false;
        }
        switch (index) {
            case 1:
                return config.isHeadsetIdEquals(id);
            case 2:
                return config.isAlgoIdEquals(id);
            default:
                return false;
        }
    }

    public void addConfig(String headsetIdStr, String commandStr, String algoIdStr, String voiceTagStr, String callFlagStr) {
        try {
            this.mConfigList.add(new VoiceConfiguration(Integer.parseInt(headsetIdStr), commandStr, Integer.parseInt(algoIdStr), voiceTagStr, Integer.parseInt(callFlagStr)));
        } catch (Exception ex) {
            String str = TAG;
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("invalid config value:");
            stringBuilder.append(ex.getMessage());
            Slog.e(str, stringBuilder.toString());
        }
    }
}
