package com.dingyi.unluactool.core.file

import com.dingyi.unluactool.common.ktx.encodeToJson
import com.dingyi.unluactool.common.ktx.inputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS

class OpenFileManager internal constructor() : FileEventListener {

    private val cacheOpenedFile = mutableMapOf<String, CacheOpenFileObject>()

    private val vfsManager = VFS.getManager()

    private var coroutineScope: CoroutineScope? = null

    suspend fun openFile(fileObject: FileObject): String = withContext(Dispatchers.IO) {
        val uri = fileObject.publicURIString
        val fileContent = kotlin.runCatching {
            fileObject.inputStream
                .readBytes()
                .decodeToString()
        }.getOrNull()
        val cacheContent = cacheOpenedFile[uri] ?: CacheOpenFileObject(
            uri = uri,
            content = fileContent
        )
        cacheContent.content = fileContent
        cacheOpenedFile[uri] = cacheContent
        cacheContent.content ?: ""
    }

    suspend fun saveFile(fileObject: FileObject, content: String?) = withContext(Dispatchers.IO) {
        val uri  = fileObject.publicURIString
        val saveContent = cacheOpenedFile[uri]?.content ?: content ?: return@withContext

        val fileObject = vfsManager.resolveFile(uri)

        fileObject.content
            .outputStream
            .bufferedWriter()
            .use {
                it.write(saveContent)
            }

    }

    suspend fun loadFileInCache(fileObject: FileObject): String {
        val uri = fileObject.publicURIString
        val cacheContent = cacheOpenedFile[uri]
        return cacheContent?.content ?: openFile(fileObject)
    }

    fun bindCoroutineScope(coroutineScope: CoroutineScope) {
        this.coroutineScope = coroutineScope
    }


    fun close() {
        cacheOpenedFile.clear()
        coroutineScope = null
    }

    override fun onEvent(event: FileEvent) {
        val cacheContent = cacheOpenedFile.get(event.targetFileUri)

        when (event) {
            is FileOpenEvent -> {

                if (cacheContent == null) {
                    coroutineScope?.launch {
                        loadFileInCache(vfsManager.resolveFile(event.targetFileUri))
                    }
                }
            }

            is FileContentChangeEvent -> {

            }


        }
    }
}

class CacheOpenFileObject(
    val uri: String,
    var content: String?
)