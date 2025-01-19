# grasskcpper
A kcp library built for a certain cute game

### Modification
* 4.6+ protocol support
* Kcp version detection support

### How to add it to your project
Add the AnimeGameServers maven repo to your build file:
```gradle
repositories {
    ...
    maven {
        name = "ags-mvn-Releases"
        url = uri("https://mvn.animegameservers.org/releases")
    }
}
```

Then add this to your dependencies in the gradle build file:

```gradle
implementation "org.anime_game_servers:grasskcpper:0.1"
```

### Credits

Anime game changes: [Simplxss](https://github.com/Simplxss)

Version 4.6+ Changes: [OcenWang-GI](https://github.com/OcenWang-GI) [Commit](https://github.com/OcenWang-GI/AyakaPS-KCP/commit/921187d53f3cbab040699fb115e49a27efa5761a)

Original library: https://github.com/l42111996/java-Kcp
