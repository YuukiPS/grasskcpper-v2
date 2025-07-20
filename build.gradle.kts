plugins {
    id("java-library")
    id("maven-publish")
}
group = "org.anime_game_servers"
version = "0.1"
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    withSourcesJar()
    withJavadocJar()
}
repositories {
    mavenCentral()
}
dependencies {
    implementation("io.netty:netty-all:4.1.90.Final")
    implementation("org.slf4j:slf4j-api:2.0.5")
    // Optional dependency - hashing will be disabled if not available
    compileOnly("net.openhft:zero-allocation-hashing:0.27ea1")
}
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "grasskcpper"
            //from components.java
            pom {
                name = "grasskcpper"
                description = "A kcp library built for a certain cute game."
                url = "https://github.com/Hartie95/grasskcpper"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
            }
        }
    }
    repositories {
        maven {
            name = "agsmvnrelease"
            url = uri("https://mvn.animegameservers.org/releases")
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
        }
    }
}
