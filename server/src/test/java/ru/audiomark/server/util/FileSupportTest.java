package ru.audiomark.server.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Функциональные тесты файлового индексирования. */
class FileSupportTest {
    @TempDir Path tempDir;

    @Test
    void identicalContentHasIdenticalMd5() throws Exception {
        Path first = Files.writeString(tempDir.resolve("first.mp3"), "audio-data");
        Path second = Files.writeString(tempDir.resolve("second.mp3"), "audio-data");
        assertEquals(FileSupport.md5(first), FileSupport.md5(second));
    }

    @Test
    void differentContentHasDifferentMd5() throws Exception {
        Path first = Files.writeString(tempDir.resolve("first.mp3"), "first");
        Path second = Files.writeString(tempDir.resolve("second.mp3"), "second");
        assertNotEquals(FileSupport.md5(first), FileSupport.md5(second));
    }

    @Test
    void supportedAudioExtensionsIgnoreCase() {
        assertTrue(FileSupport.isSupportedAudio(Path.of("CHAPTER.MP3")));
        assertTrue(FileSupport.isSupportedAudio(Path.of("part.ogg")));
        assertFalse(FileSupport.isSupportedAudio(Path.of("cover.png")));
    }

    @Test
    void directoryKeyRemovesUnsafeSeparators() {
        assertEquals("книга-тест-1", FileSupport.directoryKey(Path.of("Книга тест 1")));
    }
}

