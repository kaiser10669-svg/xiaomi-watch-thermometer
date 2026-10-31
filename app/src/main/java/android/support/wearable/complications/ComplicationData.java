package android.support.wearable.complications;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class ComplicationData implements Parcelable {

    public static final int TYPE_NOT_CONFIGURED = 1;
    public static final int TYPE_EMPTY = 2;
    public static final int TYPE_SHORT_TEXT = 3;
    public static final int TYPE_LONG_TEXT = 4;
    public static final int TYPE_RANGED_VALUE = 5;
    public static final int TYPE_ICON = 6;
    public static final int TYPE_SMALL_IMAGE = 7;
    public static final int TYPE_LARGE_IMAGE = 8;
    public static final int TYPE_NO_PERMISSION = 9;
    public static final int TYPE_NO_DATA = 10;

    private final int mType;
    private final Bundle mFields;

    public ComplicationData(int type, Bundle fields) {
        this.mType = type;
        this.mFields = fields != null ? fields : new Bundle();
    }

    private ComplicationData(Parcel in) {
        this.mType = in.readInt();
        this.mFields = in.readBundle(getClass().getClassLoader());
    }

    public int getType() {
        return mType;
    }

    public Bundle getFields() {
        return mFields;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mType);
        dest.writeBundle(this.mFields);
    }

    public static class ComplicationDataCreator implements Parcelable.Creator<ComplicationData> {
        @Override
        public ComplicationData createFromParcel(Parcel in) {
            return new ComplicationData(in);
        }

        @Override
        public ComplicationData[] newArray(int size) {
            return new ComplicationData[size];
        }
    }

    public static final Parcelable.Creator<ComplicationData> CREATOR = new ComplicationDataCreator();

    public static final class Builder {
        private final int mType;
        private final Bundle mFields = new Bundle();

        public Builder(int type) {
            this.mType = type;
        }

        public Builder setShortText(ComplicationText text) {
            mFields.putParcelable("SHORT_TEXT", text);
            return this;
        }

        public Builder setShortTitle(ComplicationText title) {
            mFields.putParcelable("SHORT_TITLE", title);
            return this;
        }

        public Builder setLongText(ComplicationText text) {
            mFields.putParcelable("LONG_TEXT", text);
            return this;
        }

        public Builder setLongTitle(ComplicationText title) {
            mFields.putParcelable("LONG_TITLE", title);
            return this;
        }

        public Builder setIcon(Icon icon) {
            mFields.putParcelable("ICON", icon);
            return this;
        }

        public Builder setRangedValue(float value) {
            mFields.putFloat("VALUE", value);
            return this;
        }

        public Builder setRangedMinValue(float minValue) {
            mFields.putFloat("MIN_VALUE", minValue);
            return this;
        }

        public Builder setRangedMaxValue(float maxValue) {
            mFields.putFloat("MAX_VALUE", maxValue);
            return this;
        }

        public Builder setTapAction(PendingIntent tapAction) {
            mFields.putParcelable("TAP_ACTION", tapAction);
            return this;
        }

        public ComplicationData build() {
            return new ComplicationData(mType, mFields);
        }
    }
}
