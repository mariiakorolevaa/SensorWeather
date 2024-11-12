import android.os.Parcel
import android.os.Parcelable

data class SensorData(
    val time: String,
    val value: Double,
    val measurementName: String,
    val sensorName: String,
    val field: String
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readDouble(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readString() ?: ""
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(time)
        parcel.writeDouble(value)
        parcel.writeString(measurementName)
        parcel.writeString(sensorName)
        parcel.writeString(field)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<SensorData> {
        override fun createFromParcel(parcel: Parcel): SensorData {
            return SensorData(parcel)
        }

        override fun newArray(size: Int): Array<SensorData?> {
            return arrayOfNulls(size)
        }
    }
}