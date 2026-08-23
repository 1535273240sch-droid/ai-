plugins {
    `java-library`
}

group = "de.robv.android.xposed"
version = "82"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.jar {
    archiveBaseName.set("api")
    archiveVersion.set("82")
    destinationDirectory.set(layout.buildDirectory.dir("libs").get())
    from(sourceSets.main.get().output)
}
