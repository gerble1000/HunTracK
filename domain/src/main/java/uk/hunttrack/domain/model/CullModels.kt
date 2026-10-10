package uk.hunttrack.domain.model

enum class CullSex(val label: String) {
    MALE("Male"),
    FEMALE("Female"),
    UNKNOWN("Unknown"),
}

enum class CullAgeClass(val label: String) {
    ADULT("Adult"),
    YEARLING("Yearling"),
    JUVENILE("Juvenile"),
    UNKNOWN("Unknown"),
}

enum class CarcassTestStatus(val label: String) {
    NOT_REQUIRED("Not required"),
    PENDING("Pending"),
    PASSED("Passed"),
    FAILED("Failed"),
    SENT("Sent for testing"),
}

data class CullRecord(
    val id: Long = 0,
    val culledAtEpochMs: Long,
    val speciesId: String?,
    val speciesLabel: String,
    val sex: CullSex = CullSex.UNKNOWN,
    val ageClass: CullAgeClass = CullAgeClass.UNKNOWN,
    val count: Int = 1,
    val latitude: Double?,
    val longitude: Double?,
    val osGridRef: String = "",
    val locationName: String = "",
    val carcassTestStatus: CarcassTestStatus = CarcassTestStatus.NOT_REQUIRED,
    val notes: String = "",
    val linkedMarkerId: Long? = null,
)
