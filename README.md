# IFile

A small Kotlin/JVM library that puts file operations behind the `IFile` interface. `UseFile` is the real filesystem implementation. `FakeFile` is included as an alternate `IFile` implementation for tests that want to override the real filesystem without touching disk.

## Use

```kotlin
dependencies {
    implementation("no.iktdev.library:files:1.0.0")
}
```

Add the Reposilite releases repository to the consuming project's dependency repositories. Use the snapshots repository when consuming a `-SNAPSHOT` version.

Hashing is part of `IFile`; both helpers return the included `FileHash` type, so consumers do not need a separate hashing dependency or imports:

```kotlin
val sha256: FileHash = IFile("video.mkv").toSha256()
val xxHash: FileHash = IFile("video.mkv").toXxHash()
```

Tests can set the existing factory to use the fake implementation:

```kotlin
@BeforeEach
fun setUp() {
    FakeFile.wipe()
    IFile.factory = { path -> FakeFile(path) }
}
```

`FakeFile` is intentionally a test implementation, not a full emulation of every platform filesystem behavior. `toJavaFile()` is unsupported by the fake.

`IFile.normalized()` collapses `.` and `..` path segments without following symbolic links. `IFile.realPath()` follows symbolic links and requires the target to exist. To check that a referenced file stays under a root directory, compare canonical `Path` values:

```kotlin
val root = contentFolder.realPath()
val candidate = contentFolder.resolve("media/video.mkv").realPath()
require(candidate.startsWith(root))
```

`FakeFile.symbolicLinkTo()` can model links in tests; `realPath()` resolves those links against the fake file registry.

## Publish

Publish locally with:

```shell
./gradlew publishToMavenLocal
```

Publish to Reposilite with:

```shell
REPOSILITE_USERNAME=... REPOSILITE_PASSWORD=... ./gradlew publish
```

Set `releaseVersion` or `RELEASE_VERSION` for a release version. The default is `1.0.0-SNAPSHOT`; repository URLs can be overridden with `REPOSILITE_SNAPSHOT_URL` and `REPOSILITE_RELEASE_URL`.
