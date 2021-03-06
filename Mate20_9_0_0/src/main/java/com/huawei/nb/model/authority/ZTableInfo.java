package com.huawei.nb.model.authority;

import android.database.Cursor;
import android.os.Parcel;
import android.os.Parcelable.Creator;
import com.huawei.odmf.core.AManagedObject;
import com.huawei.odmf.model.AEntityHelper;

public class ZTableInfo extends AManagedObject {
    public static final Creator<ZTableInfo> CREATOR = new Creator<ZTableInfo>() {
        public ZTableInfo createFromParcel(Parcel in) {
            return new ZTableInfo(in);
        }

        public ZTableInfo[] newArray(int size) {
            return new ZTableInfo[size];
        }
    };
    private Integer authorityLevel;
    private Integer authorityValue;
    private Long id;
    private String reserved;
    private String tableDesc;
    private Long tableId;
    private String tableName;

    public ZTableInfo(Cursor cursor) {
        Integer num = null;
        setRowId(Long.valueOf(cursor.getLong(0)));
        this.id = cursor.isNull(1) ? null : Long.valueOf(cursor.getLong(1));
        this.tableId = cursor.isNull(2) ? null : Long.valueOf(cursor.getLong(2));
        this.tableName = cursor.getString(3);
        this.tableDesc = cursor.getString(4);
        this.authorityLevel = cursor.isNull(5) ? null : Integer.valueOf(cursor.getInt(5));
        if (!cursor.isNull(6)) {
            num = Integer.valueOf(cursor.getInt(6));
        }
        this.authorityValue = num;
        this.reserved = cursor.getString(7);
    }

    public ZTableInfo(Parcel in) {
        String str = null;
        super(in);
        if (in.readByte() == (byte) 0) {
            this.id = null;
            in.readLong();
        } else {
            this.id = Long.valueOf(in.readLong());
        }
        this.tableId = in.readByte() == (byte) 0 ? null : Long.valueOf(in.readLong());
        this.tableName = in.readByte() == (byte) 0 ? null : in.readString();
        this.tableDesc = in.readByte() == (byte) 0 ? null : in.readString();
        this.authorityLevel = in.readByte() == (byte) 0 ? null : Integer.valueOf(in.readInt());
        this.authorityValue = in.readByte() == (byte) 0 ? null : Integer.valueOf(in.readInt());
        if (in.readByte() != (byte) 0) {
            str = in.readString();
        }
        this.reserved = str;
    }

    private ZTableInfo(Long id, Long tableId, String tableName, String tableDesc, Integer authorityLevel, Integer authorityValue, String reserved) {
        this.id = id;
        this.tableId = tableId;
        this.tableName = tableName;
        this.tableDesc = tableDesc;
        this.authorityLevel = authorityLevel;
        this.authorityValue = authorityValue;
        this.reserved = reserved;
    }

    public int describeContents() {
        return 0;
    }

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
        setValue();
    }

    public Long getTableId() {
        return this.tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
        setValue();
    }

    public String getTableName() {
        return this.tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
        setValue();
    }

    public String getTableDesc() {
        return this.tableDesc;
    }

    public void setTableDesc(String tableDesc) {
        this.tableDesc = tableDesc;
        setValue();
    }

    public Integer getAuthorityLevel() {
        return this.authorityLevel;
    }

    public void setAuthorityLevel(Integer authorityLevel) {
        this.authorityLevel = authorityLevel;
        setValue();
    }

    public Integer getAuthorityValue() {
        return this.authorityValue;
    }

    public void setAuthorityValue(Integer authorityValue) {
        this.authorityValue = authorityValue;
        setValue();
    }

    public String getReserved() {
        return this.reserved;
    }

    public void setReserved(String reserved) {
        this.reserved = reserved;
        setValue();
    }

    public void writeToParcel(Parcel out, int ignored) {
        super.writeToParcel(out, ignored);
        if (this.id != null) {
            out.writeByte((byte) 1);
            out.writeLong(this.id.longValue());
        } else {
            out.writeByte((byte) 0);
            out.writeLong(1);
        }
        if (this.tableId != null) {
            out.writeByte((byte) 1);
            out.writeLong(this.tableId.longValue());
        } else {
            out.writeByte((byte) 0);
        }
        if (this.tableName != null) {
            out.writeByte((byte) 1);
            out.writeString(this.tableName);
        } else {
            out.writeByte((byte) 0);
        }
        if (this.tableDesc != null) {
            out.writeByte((byte) 1);
            out.writeString(this.tableDesc);
        } else {
            out.writeByte((byte) 0);
        }
        if (this.authorityLevel != null) {
            out.writeByte((byte) 1);
            out.writeInt(this.authorityLevel.intValue());
        } else {
            out.writeByte((byte) 0);
        }
        if (this.authorityValue != null) {
            out.writeByte((byte) 1);
            out.writeInt(this.authorityValue.intValue());
        } else {
            out.writeByte((byte) 0);
        }
        if (this.reserved != null) {
            out.writeByte((byte) 1);
            out.writeString(this.reserved);
            return;
        }
        out.writeByte((byte) 0);
    }

    public AEntityHelper<ZTableInfo> getHelper() {
        return ZTableInfoHelper.getInstance();
    }

    public String getEntityName() {
        return "com.huawei.nb.model.authority.ZTableInfo";
    }

    public String getDatabaseName() {
        return "dsWeather";
    }

    public String toString() {
        StringBuilder sb = new StringBuilder("ZTableInfo { id: ").append(this.id);
        sb.append(", tableId: ").append(this.tableId);
        sb.append(", tableName: ").append(this.tableName);
        sb.append(", tableDesc: ").append(this.tableDesc);
        sb.append(", authorityLevel: ").append(this.authorityLevel);
        sb.append(", authorityValue: ").append(this.authorityValue);
        sb.append(", reserved: ").append(this.reserved);
        sb.append(" }");
        return sb.toString();
    }

    public boolean equals(Object o) {
        return super.equals(o);
    }

    public int hashCode() {
        return super.hashCode();
    }

    public String getDatabaseVersion() {
        return "0.0.16";
    }

    public int getDatabaseVersionCode() {
        return 16;
    }

    public String getEntityVersion() {
        return "0.0.1";
    }

    public int getEntityVersionCode() {
        return 1;
    }
}
