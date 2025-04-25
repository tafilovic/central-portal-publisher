package io.github.tafilovic

enum class PublishingType(val type: String) {
    USER_MANAGED("USER_MANAGED"),
    AUTOMATIC("AUTOMATIC");

    companion object {
        fun parseFrom(type: String?): PublishingType {
            if (type == null) return USER_MANAGED
            return when (type) {
                AUTOMATIC.type -> AUTOMATIC
                else -> USER_MANAGED
            }
        }
    }
}