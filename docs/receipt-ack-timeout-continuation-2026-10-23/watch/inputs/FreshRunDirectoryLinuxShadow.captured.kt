package com.hugr.wearos

import android.system.StructStat
import java.io.File
import java.io.FileDescriptor
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.IdentityHashMap
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLinux

/**
 * TEST ONLY. Robolectric's Linux shadow opens paths through RandomAccessFile, which cannot open
 * a directory. Receipt publication opens the actual disposable Linux directory then fsyncs it.
 * This shadow keeps that force-sync and performs the real atomic filesystem rename; it does not
 * replace durability with a no-op.
 *
 * Physical power-loss durability and Android's real Os implementation remain device questions.
 */
@Implements(className = "libcore.io.Linux", isInAndroidSdk = false)
class FreshRunDirectoryLinuxShadow : ShadowLinux() {
    private val directories = IdentityHashMap<FileDescriptor, Pair<String, FileChannel>>()

    @Implementation
    override fun open(path: String, flags: Int, mode: Int): FileDescriptor {
        if (!File(path).isDirectory) return super.open(path, flags, mode)
        val descriptor = FileDescriptor()
        directories[descriptor] = path to FileChannel.open(File(path).toPath(), StandardOpenOption.READ)
        return descriptor
    }

    @Implementation
    override fun fstat(fd: FileDescriptor): StructStat {
        val directory = directories[fd] ?: return super.fstat(fd)
        return super.stat(directory.first)
    }

    @Implementation
    fun fsync(fd: FileDescriptor) {
        val directory = directories[fd]
            ?: throw IllegalStateException("Unexpected synthetic directory fsync descriptor")
        directory.second.force(true)
    }

    @Implementation
    fun close(fd: FileDescriptor) {
        val directory = directories.remove(fd)
            ?: throw IllegalStateException("Unexpected synthetic directory close descriptor")
        directory.second.close()
    }

    @Implementation
    fun rename(from: String, to: String) {
        Files.move(File(from).toPath(), File(to).toPath(), StandardCopyOption.ATOMIC_MOVE)
    }
}
