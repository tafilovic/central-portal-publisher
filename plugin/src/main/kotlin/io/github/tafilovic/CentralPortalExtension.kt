package io.github.tafilovic

abstract class CentralPortalExtension {
    var groupId: String? = null
    var artifactId: String? = null
    var version: String? = null
    var flavorName: String? = null
    var componentName: String = "release"
    /** Upload call timeout in minutes. Increase for large bundles. Default: 10. */
    var uploadTimeoutMinutes: Long = 10
}