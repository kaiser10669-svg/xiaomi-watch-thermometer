package android.support.wearable.complications;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;

public class ComplicationText implements Parcelable {

    private final CharSequence mSurroundingText;

    public ComplicationText(CharSequence text) {
        this.mSurroundingText = text != null ? text : "";
    }

    public static ComplicationText plainText(CharSequence text) {
        return new ComplicationText(text);
    }

    public CharSequence getText() {
        return mSurroundingText;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        Bundle bundle = new Bundle();
        bundle.putCharSequence("surrounding_string", this.mSurroundingText);
        dest.writeBundle(bundle);
    }

    public static class ComplicationTextCreator implements Parcelable.Creator<ComplicationText> {
        @Override
        public ComplicationText createFromParcel(Parcel in) {
            Bundle bundle = in.readBundle(ComplicationText.class.getClassLoader());
            CharSequence text = bundle != null ? bundle.getCharSequence("surrounding_string") : "";
            return new ComplicationText(text);
        }

        @Override
        public ComplicationText[] newArray(int size) {
            return new ComplicationText[size];
        }
    }

    public static final Parcelable.Creator<ComplicationText> CREATOR = new ComplicationTextCreator();
}
