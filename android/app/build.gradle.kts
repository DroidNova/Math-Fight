import org.gradle.api.DefaultTask
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

abstract class ExtractGdxNativesTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val arm64: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val arm32: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val x64: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val x86: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val archives: ArchiveOperations

    @get:Inject
    abstract val files: FileSystemOperations

    @TaskAction
    fun extract() {
        files.delete { delete(outputDirectory) }
        copyAbi(arm64, "arm64-v8a")
        copyAbi(arm32, "armeabi-v7a")
        copyAbi(x64, "x86_64")
        copyAbi(x86, "x86")
    }

    private fun copyAbi(artifacts: ConfigurableFileCollection, abi: String) {
        files.copy {
            artifacts.files.forEach { from(archives.zipTree(it)) }
            include("*.so")
            into(outputDirectory.dir(abi))
        }
    }
}

val gdxNativesArm64 by configurations.creating
val gdxNativesArm32 by configurations.creating
val gdxNativesX64 by configurations.creating
val gdxNativesX86 by configurations.creating

android {
    namespace = "com.droidnova.mathfight"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.droidnova.mathfight"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val taskName = "extract${variant.name.replaceFirstChar { it.uppercase() }}GdxNatives"
        val extractTask = tasks.register<ExtractGdxNativesTask>(taskName) {
            arm64.from(gdxNativesArm64)
            arm32.from(gdxNativesArm32)
            x64.from(gdxNativesX64)
            x86.from(gdxNativesX86)
        }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(
            extractTask,
            ExtractGdxNativesTask::outputDirectory
        )
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.fragment)
    implementation(libs.gdx)
    implementation(libs.gdx.backend.android)
    gdxNativesArm64(variantOf(libs.gdx.platform) { classifier("natives-arm64-v8a") })
    gdxNativesArm32(variantOf(libs.gdx.platform) { classifier("natives-armeabi-v7a") })
    gdxNativesX64(variantOf(libs.gdx.platform) { classifier("natives-x86_64") })
    gdxNativesX86(variantOf(libs.gdx.platform) { classifier("natives-x86") })
    implementation(libs.socket.io.client) {
        exclude(group = "org.json", module = "json")
    }
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
