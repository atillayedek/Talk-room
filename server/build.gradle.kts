plugins {
    application
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
}

application {
    mainClass.set("com.talkroom.server.ApplicationKt")
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:3.0.3")
    implementation("io.ktor:ktor-server-netty-jvm:3.0.3")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:3.0.3")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:3.0.3")
    implementation("io.ktor:ktor-client-java-jvm:3.0.3")
    implementation("io.ktor:ktor-client-content-negotiation-jvm:3.0.3")
    implementation("io.ktor:ktor-client-logging-jvm:3.0.3")
    implementation("io.ktor:ktor-client-websockets-jvm:3.0.3")
    implementation("io.agora:authentication:2.1.1")
    implementation("at.favre.lib:bcrypt:0.10.2")
    implementation("ch.qos.logback:logback-classic:1.5.19")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
}
