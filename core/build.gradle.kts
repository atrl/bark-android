plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("org.json:json:20240303")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.21")
    testImplementation("junit:junit:4.13.2")
}
