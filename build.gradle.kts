plugins {
    id("java-library")
    id("maven-publish")
}
group = "org.anime_game_servers"
version = "0.1"
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
repositories {
    mavenCentral()
}
dependencies {
    implementation("io.netty:netty-all:4.1.90.Final")
    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("net.openhft:zero-allocation-hashing:0.26ea0")
}
publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            artifactId = "grasskcpper"
            //from components.java
            pom {
                name = "Grasscutter"
                description = "A server software reimplementation for an anime game."
                url = "https://github.com/Grasscutters/Grasscutter"
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
