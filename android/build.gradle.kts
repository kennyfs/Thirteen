plugins {
  id("com.android.application") version "9.5.0" apply false
  // AGP compiles Kotlin itself (built-in Kotlin); these set the Kotlin version it uses and add the plugins.
  id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
  id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
  id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
