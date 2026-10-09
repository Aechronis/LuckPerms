# LuckPerms for Minestom

An embedded LuckPerms library for **Minestom `2026.10.05-26.2`**, Minecraft 26.2,
and Java 25. The `master` branch follows upstream LuckPerms and preserves the
builder, configuration, command registration, and context provider APIs from
[Javaniac's Minestom fork](https://codeberg.org/Javaniac/LuckPerms).

## Build

```sh
./gradlew -PminestomOnly :common:test :minestom:build
```

The distributable is `minestom/build/libs/luckperms-minestom-*-minestom-SNAPSHOT.jar`.
It includes LuckPerms, its API, and the default H2 storage driver. Minestom,
Adventure, and the logging backend are supplied by your server. When adding the
jar directly, also add `net.kyori:adventure-text-minimessage:5.2.0`.

For a local Maven dependency:

```sh
./gradlew -PminestomOnly -PminestomVersion=5.5-minestom-local :minestom:publishToMavenLocal
```

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("net.minestom:minestom:2026.10.05-26.2")
    implementation("net.aechronis:luckperms-minestom:5.5-minestom-local")
}
```

These are local build instructions; this fork has not been published to Maven Central.

## Use

Initialize Minestom first, then enable LuckPerms before accepting players:

```java
MinecraftServer server = MinecraftServer.init(new Auth.Offline());

LuckPerms luckPerms = LuckPermsMinestom.builder(Path.of("luckperms"))
        .commandRegistry(CommandRegistry.minestom())
        .configurationAdapter(HoconConfigurationAdapter::new)
        .enable();

MinecraftServer.getSchedulerManager().buildShutdownTask(LuckPermsMinestom::disable);
```

Imports for the adapter are in `me.lucko.luckperms.minestom`; the HOCON adapter is
`me.lucko.luckperms.minestom.init.HoconConfigurationAdapter`. The returned API is
`net.luckperms.api.LuckPerms`.

The builder defaults to environment variable configuration. Use the HOCON adapter
above for a generated `luckperms.conf`, or retain an existing
`EnvironmentVariableConfigAdapter`/custom configuration adapter. Commands are
opt-in; the registry above enables `/luckperms`, `/lp`, `/perm`, `/perms`,
`/permission`, and `/permissions`. Custom lifecycle owners can keep using
`.commandRegistry(registerConsumer, unregisterConsumer)`.

After login, check permissions through the LuckPerms API:

```java
User user = luckPerms.getUserManager().getUser(player.getUuid());
boolean allowed = user != null && user.getCachedData().getPermissionData()
        .checkPermission("example.permission").asBoolean();
```

Player data loads during login and unloads after disconnect. If enabling the
library while players are already connected, load those users with
`getUserManager().loadUser(uuid, username)` before checking permissions.
Call `LuckPermsMinestom.disable()` before replacing the library or stopping the
server; it removes its event node and unregisters its commands.

Game mode and dimension contexts are registered by default. Add custom contexts
through `.contextProvider(...)` or disable contexts in the configuration.

Like the original embedded fork, this library does not download dependencies at
runtime. H2 is bundled. Other storage or messaging backends require their drivers
on your application's runtime classpath (for example HikariCP plus the database
JDBC driver for remote SQL, or Jedis for Redis).
Legacy flat-file storage backends also require their Configurate 3 dependencies;
the included Configurate 4 adapter handles the library's configuration file.

## Upstream and attribution

This is an unofficial Minestom integration, licensed under LuckPerms' MIT license.
The adapter is derived from Javaniac's fork at
`7de30d32b74b6edf3f4196fecccd0abcd8532866`, including work by LU15/LooFifteen and
other LuckPerms contributors. Existing source copyright headers are retained.
Other upstream platforms remain in the repository. `-PminestomOnly` selects the
small project set needed for this library; omit it for the upstream project set.

To bring in upstream changes, merge `LuckPerms/LuckPerms`'s `master` into this
fork's `master` branch and rerun the build above. Integration checks run against
both the normal classpath and the packaged jar with the target Minestom version.
