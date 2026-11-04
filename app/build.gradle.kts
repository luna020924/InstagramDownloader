plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.instasharedownloader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.instasharedownloader"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.2.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20251224")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                output.versionName.map { versionName ->
                    "InstaShareDownloader-$versionName-${variant.name}.apk"
                }
            )
        }
    }
}
