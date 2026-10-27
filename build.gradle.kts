plugins {
    // AGP 9 já inclui o suporte a Kotlin (não precisa do plugin kotlin-android)
    id("com.android.application") version "9.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.10" apply false
}
