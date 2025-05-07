package io.github.tafilovic

import io.github.tafilovic.Hash.md5
import io.github.tafilovic.Hash.sha1
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.io.FileOutputStream
import java.util.Base64
import java.util.Properties
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A simple 'hello world' plugin.
 */
class CentralPortalPublisherPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        plugins.apply("maven-publish")

        val localProperties = Properties().apply {
            val localFile = project.rootProject.file("local.properties")
            if (localFile.exists()) {
                load(localFile.inputStream())
            }
        }

        val androidComponents =
            project.extensions.findByName("android") as? com.android.build.gradle.LibraryExtension
                ?: throw GradleException("Android library plugin is not applied")

        val sourcesJar = project.tasks.register("sourcesJar", Jar::class.java) {
            archiveClassifier.set("sources")
            from(androidComponents.sourceSets.getByName("main").java.srcDirs)
        }

        val javadocJar = project.tasks.register("javadocJar", Jar::class.java) {
            archiveClassifier.set("javadoc")
            from(project.layout.buildDirectory.dir("docs/javadoc"))
        }

        project.afterEvaluate {
//            val androidComponent = project.components.findByName("release")
//            if (androidComponent == null) {
//                logger.error("❌ 'release' component not found — make sure this is an Android library module")
//                return@afterEvaluate
//            }

            project.extensions.configure(PublishingExtension::class.java) {
                publications {
                    val mavenPublication = (findByName("maven") as? MavenPublication) ?: create(
                        "maven",
                        MavenPublication::class.java
                    )

                    mavenPublication.apply {
                        //from(androidComponent)
                        groupId = project.findProperty("GROUP")?.toString()
                        artifactId = project.findProperty("POM_ARTIFACT_ID")?.toString()
                        version = project.findProperty("VERSION_NAME")?.toString()

                        // IMPORTANT: sources and javadoc JARs MUST have classifiers and must NOT replace the AAR
                        if (!artifacts.any { it.classifier == "sources" && it.extension == "jar" }) {
                            artifact(sourcesJar.get()) {
                                classifier = "sources"
                                extension = "jar" // Optional but explicit
                            }
                        }

                        if (!artifacts.any { it.classifier == "javadoc" && it.extension == "jar" }) {
                            artifact(javadocJar.get()) {
                                classifier = "javadoc"
                                extension = "jar"
                            }
                        }

                        pom {
                            name.set(project.findProperty("POM_NAME")?.toString())
                            description.set(project.findProperty("POM_DESCRIPTION")?.toString())
                            url.set(project.findProperty("POM_URL")?.toString())

                            licenses {
                                license {
                                    name.set(project.findProperty("POM_LICENSE_NAME")?.toString())
                                    url.set(project.findProperty("POM_LICENSE_URL")?.toString())
                                    distribution.set(
                                        project.findProperty("POM_LICENSE_DIST")?.toString()
                                    )
                                }
                            }

                            developers {
                                developer {
                                    id.set(project.findProperty("POM_DEVELOPER_ID")?.toString())
                                    name.set(project.findProperty("POM_DEVELOPER_NAME")?.toString())
                                    url.set(project.findProperty("POM_DEVELOPER_URL")?.toString())
                                }
                            }

                            scm {
                                url.set(project.findProperty("POM_SCM_URL")?.toString())
                                connection.set(
                                    project.findProperty("POM_SCM_CONNECTION")?.toString()
                                )
                                developerConnection.set(
                                    project.findProperty("POM_SCM_DEV_CONNECTION")?.toString()
                                )
                            }
                        }
                    }
                }
            }

            project.tasks.matching { it.name == "generateMetadataFileForMavenPublication" }
                .configureEach {
                    dependsOn(sourcesJar, javadocJar)
                }


            project.pluginManager.apply("signing")
            extensions.configure(org.gradle.plugins.signing.SigningExtension::class.java) {
                if(localProperties.containsKey("signing.keyId") &&
                    localProperties.containsKey("signing.password") &&
                    localProperties.containsKey("signing.secretKeyRingFile")) {
                    val signingKeyId = localProperties.getValue("signing.keyId") as String?
                    val signingPassword = localProperties.getValue("signing.password") as String?
                    val signingKeyFile = localProperties.getValue("signing.secretKeyRingFile")
                        ?.let { file(it).readText() }

                    println("SigningKey: $signingKeyId")

                    useInMemoryPgpKeys(signingKeyId, signingKeyFile, signingPassword)
                    sign(extensions.getByType(PublishingExtension::class.java).publications)
                    println("✅ Signed artifacts! ")
                }
            }
        }

        val packageArtifacts = tasks.register("packageArtifacts") {
            group = "publishing"
            dependsOn(project.tasks.getByName("clean"))
            dependsOn("publishToMavenLocal")

            doLast {
                val groupId = project.findProperty("GROUP").toString()
                val artifactId = project.findProperty("POM_ARTIFACT_ID").toString()
                val version = project.findProperty("VERSION_NAME").toString()

                getOutputsFiles(artifactId, version)
                getLibsFiles(artifactId, version)
                getPublicationsFiles(artifactId, version)

                val filesToZip =
                    layout.buildDirectory.dir("central-portal-bundle").get().asFile.listFiles()

                val outputZip = layout.buildDirectory.file("central-portal-upload.zip").get().asFile
                ZipOutputStream(FileOutputStream(outputZip)).use { zipOut ->
                    filesToZip?.forEach { file ->
                        zipOut.putNextEntry(
                            ZipEntry(
                                "${
                                    groupId.replace(
                                        ".", "/"
                                    )
                                }/$artifactId/$version/${file.name}"
                            )
                        )
                        file.inputStream().use { it.copyTo(zipOut) }
                        zipOut.closeEntry()
                    }
                }

                println("✅ Packaged artifacts into ${outputZip.name}")
            }
        }

        tasks.register("fakeUpload") {
            group = "publishing"
            dependsOn(packageArtifacts)
        }

        tasks.register("uploadToCentralPortal") {
            group = "publishing"
            dependsOn(packageArtifacts)
            val pomVersion = project.findProperty("VERSION_NAME").toString()
            val pomName = project.findProperty("POM_NAME").toString()
            val publishingTypeValue = project.findProperty("POM_PUBLISHING_TYPE").toString()

            doLast {
                val username = localProperties.getValue("mavenCentralUsername").toString()
                val password = localProperties.getValue("mavenCentralPassword").toString()
                val uploadFile = layout.buildDirectory.dir("central-portal-upload.zip").get().asFile
                val publishingType = PublishingType.parseFrom(publishingTypeValue)

                upload(
                    username = username,
                    password = password,
                    bundleFile = uploadFile,
                    title = "$pomName $pomVersion",
                    publishingType = publishingType
                )
            }
        }

    }

    private fun Project.getOutputsFiles(artifactId: String, version: String) {
        val bundleDir = layout.buildDirectory.dir("central-portal-bundle").get()
        val outputsFiles = layout.buildDirectory.files("outputs/aar").asFileTree.files
        outputsFiles.forEach { file ->
            val ext = if (file.name.endsWith(".aar")) "aar" else "aar.asc"
            val updatedName = "$artifactId-$version.${ext}"
            val target = bundleDir.asFile.resolve(updatedName)

            file.copyTo(target = target, overwrite = true)

            if (ext == "aar") {
                val md5 = bundleDir.asFile.resolve("$artifactId-$version.$ext.md5")
                val md5Data = target.md5().toByteArray()
                md5.writeBytes(md5Data)

                val sha1 = bundleDir.asFile.resolve("$artifactId-$version.$ext.sha1")
                val sha1Data = target.sha1().toByteArray()
                sha1.writeBytes(sha1Data)
            }
        }
    }

    private fun Project.getLibsFiles(artifactId: String, version: String) {
        val bundleDir = layout.buildDirectory.dir("central-portal-bundle").get()
        val libFiles = layout.buildDirectory.files("libs").asFileTree.files

        libFiles.forEach { file ->
            val ext = if (file.name.endsWith(".jar")) "jar" else "jar.asc"
            val suffix = if (file.name.contains("javadoc")) "javadoc" else "sources"
            val updatedName = "$artifactId-$version-$suffix.${ext}"
            val target = bundleDir.asFile.resolve(updatedName)

            file.copyTo(target = target, overwrite = true)

            if (ext == "jar") {
                val md5 = bundleDir.asFile.resolve("$artifactId-$version-$suffix.$ext.md5")
                val md5Data = target.md5().toByteArray()
                md5.writeBytes(md5Data)

                val sha1 = bundleDir.asFile.resolve("$artifactId-$version-$suffix.$ext.sha1")
                val sha1Data = target.sha1().toByteArray()
                sha1.writeBytes(sha1Data)
            }
        }
    }

    private fun Project.getPublicationsFiles(artifactId: String, version: String) {
        val bundleDir = layout.buildDirectory.dir("central-portal-bundle").get()
        val libFiles = layout.buildDirectory.files("publications/maven").asFileTree.files

        libFiles.forEach { file ->
            val ext = if (file.name.endsWith(".xml")) "pom" else "pom.asc"
            val updatedName = "$artifactId-$version.${ext}"
            val target = bundleDir.asFile.resolve(updatedName)

            file.copyTo(target = target, overwrite = true)

            if (ext == "pom") {
                val md5 = bundleDir.asFile.resolve("$artifactId-$version.$ext.md5")
                val md5Data = target.md5().toByteArray()
                md5.writeBytes(md5Data)

                val sha1 = bundleDir.asFile.resolve("$artifactId-$version.$ext.sha1")
                val sha1Data = target.sha1().toByteArray()
                sha1.writeBytes(sha1Data)
            }
        }
    }

    private fun upload(
        username: String,
        password: String,
        bundleFile: File,
        title: String,
        publishingType: PublishingType = PublishingType.USER_MANAGED
    ) {
        val loggerInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val token = Base64.getEncoder().encodeToString("$username:$password".toByteArray())

        val client = OkHttpClient.Builder().addInterceptor(loggerInterceptor)
            .callTimeout(5, TimeUnit.MINUTES).build()

        val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(
            "bundle",
            bundleFile.name,
            bundleFile.asRequestBody("application/octet-stream".toMediaType())
        ).build()

        val url = HttpUrl.Builder().scheme("https").host("central.sonatype.com")
            .addPathSegments("api/v1/publisher/upload")
            .addQueryParameter("publishingType", publishingType.type)
            .addQueryParameter("name", title)
            .build()

        val request =
            Request.Builder().url(url).addHeader("Authorization", "Bearer $token").post(requestBody)
                .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: ""
            if (response.isSuccessful) {
                println("✅ Upload successful: $responseBody")
            } else {
                println("❌ Upload failed: ${response.code} ${response.message}\n$responseBody")
            }
        }
    }
}