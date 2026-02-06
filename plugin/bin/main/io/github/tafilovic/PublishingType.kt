package io.github.tafilovic

enum class PublishingType(val type: String) {
    // (default) a deployment will go through validation and require the user to manually publish it via the Portal UI
    USER_MANAGED("USER_MANAGED"),
    
    // a deployment will go through validation and, if it passes, automatically proceed to publish to Maven Central
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