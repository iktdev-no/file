package no.iktdev.files

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IFileRealPathTest {
    @AfterTest
    fun resetFactory() {
        IFile.factory = { UseFile(it) }
        FakeFile.wipe()
    }

    @Test
    fun realPathFollowsSymbolicLink() {
        val temp = Files.createTempDirectory("ifile-real-path")
        try {
            val root = Files.createDirectory(temp.resolve("root"))
            val file = Files.writeString(root.resolve("media.mkv"), "media")
            val link = Files.createSymbolicLink(temp.resolve("link.mkv"), file)
            val outside = Files.writeString(temp.resolve("outside.mkv"), "outside")
            val escapingLink = Files.createSymbolicLink(root.resolve("outside.mkv"), outside)

            assertEquals(file.toRealPath(), UseFile(link.toString()).realPath())
            assertTrue(UseFile(link.toString()).realPath().startsWith(UseFile(root.toString()).realPath()))
            assertTrue(!UseFile(escapingLink.toString()).realPath().startsWith(UseFile(root.toString()).realPath()))
        } finally {
            temp.toFile().deleteRecursively()
        }
    }

    @Test
    fun realPathRejectsMissingPathsAndFakeLinksResolveOutsideRoot() {
        val root = FakeFile("/fake/root", directory = true)
        val outside = FakeFile("/fake/outside", directory = true)
        FakeFile("/fake/outside/secret.mkv")
        val link = FakeFile("/fake/root/secret.mkv")
        link.symbolicLinkTo(outside.absolutePath + "/secret.mkv")

        assertEquals(Path.of("/fake/outside/secret.mkv"), link.realPath())
        assertTrue(!link.realPath().startsWith(root.realPath()))
        assertFailsWith<java.nio.file.NoSuchFileException> {
            FakeFile("/fake/root/missing.mkv", exists = false).realPath()
        }
    }

    @Test
    fun normalizedCollapsesDotSegmentsWithoutChangingFakeFileRegistry() {
        FakeFile.wipe()
        IFile.factory = { FakeFile(it) }
        val normalized = IFile("/fake/root/../media/./video.mkv").normalized()

        assertEquals("/fake/media/video.mkv", normalized.path)
    }
}
