plugins {
    java
    application
}

repositories {
    mavenCentral()
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    implementation("org.geysermc.mcprotocollib:protocol:26.3-2")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

application {
    mainClass = "io.github.adrielorihuela.legacyfolia.bots.Main"
}

// Copies the runtime classpath so the run scripts can start the bench with a plain `java -cp`.
tasks.register<Copy>("copyDeps") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("deps"))
}
