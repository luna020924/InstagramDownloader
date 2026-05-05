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
        versionCode = 1
        versionName = "0.1.0"
    }
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
