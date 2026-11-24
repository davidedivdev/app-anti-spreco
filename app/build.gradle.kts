plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------------------------
// Monetizzazione: i valori si cambiano in gradle.properties (vedi i commenti lì).
// Finché gli ID AdMob sono vuoti si usano gli ID di PROVA di Google: annunci finti, nessun guadagno.
// ---------------------------------------------------------------------------------------------
val testAdmobAppId = "ca-app-pub-3940256099942544~3347511713"
val testBannerId = "ca-app-pub-3940256099942544/9214589741"
fun prop(name: String): String = (project.findProperty(name) as String?)?.trim().orEmpty()
val adsEnabled = prop("SALVACIBO_ADS_ENABLED").ifEmpty { "true" }.toBoolean()
val billingEnabled = prop("SALVACIBO_BILLING_ENABLED").ifEmpty { "true" }.toBoolean()

android {
    namespace = "com.davide.salvacibo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.davide.salvacibo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("boolean", "ADS_ENABLED", adsEnabled.toString())
        buildConfigField("boolean", "BILLING_ENABLED", billingEnabled.toString())
    }

    buildTypes {
        debug {
            // in debug sempre annunci di prova (cliccare annunci veri dal proprio telefono è vietato)
            manifestPlaceholders["admobAppId"] = testAdmobAppId
            buildConfigField("String", "ADMOB_BANNER_ID", "\"$testBannerId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["admobAppId"] = prop("ADMOB_APP_ID").ifEmpty { testAdmobAppId }
            buildConfigField("String", "ADMOB_BANNER_ID", "\"${prop("ADMOB_BANNER_ID").ifEmpty { testBannerId }}\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    // Database locale
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Notifiche giornaliere
    implementation(libs.androidx.work.runtime.ktx)

    // Fotocamera + riconoscimento (offline, sul telefono)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.text.recognition)

    // Immagini da Open Food Facts
    implementation(libs.coil.compose)

    // Pubblicità (AdMob) + consenso privacy UE (UMP), acquisti in-app, recensioni in-app
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing)
    implementation(libs.play.review)

    testImplementation(libs.junit)
}
