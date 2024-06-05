package me.devsaki.hentoid.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.devsaki.hentoid.R
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.enums.StorageLocation
import me.devsaki.hentoid.events.ProcessEvent
import me.devsaki.hentoid.util.Preferences
import me.devsaki.hentoid.util.StringHelper
import me.devsaki.hentoid.util.detachAllExternalContent
import me.devsaki.hentoid.util.detachAllPrimaryContent
import me.devsaki.hentoid.util.file.Beholder
import me.devsaki.hentoid.util.file.copyFile
import me.devsaki.hentoid.util.file.getDocumentFromTreeUriString
import me.devsaki.hentoid.util.file.listFiles
import me.devsaki.hentoid.util.getOrCreateContentDownloadDir
import me.devsaki.hentoid.util.getPathRoot
import org.greenrobot.eventbus.EventBus
import java.util.concurrent.atomic.AtomicInteger

class PreferencesViewModel(application: Application, val dao: CollectionDAO) :
    AndroidViewModel(application) {

    override fun onCleared() {
        super.onCleared()
        dao.cleanup()
    }

    fun detach(location: StorageLocation) {
        when (location) {
            StorageLocation.PRIMARY_1,
            StorageLocation.PRIMARY_2 -> {
                detachAllPrimaryContent(
                    dao,
                    location
                )
            }

            StorageLocation.EXTERNAL -> {
                detachAllExternalContent(
                    getApplication<Application>().applicationContext,
                    dao
                )
                dao.cleanupOrphanAttributes()
                Beholder.clearSnapshot(getApplication())
            }

            else -> {
                // Nothing
            }
        }
        Preferences.setStorageUri(location, "")
    }

    suspend fun merge2to1(nbBooks: Int) {
        val nbOK = AtomicInteger(0)
        val nbKO = AtomicInteger(0)
        withContext(Dispatchers.IO) {
            val ids = ArrayList<Long>()
            dao.streamAllInternalBooks(
                getPathRoot(StorageLocation.PRIMARY_2), false
            ) { c -> ids.add(c.id) }
            ids.forEach {
                mergeTo1(it, nbOK, nbKO, nbBooks)
            }
            EventBus.getDefault().postSticky(
                ProcessEvent(
                    ProcessEvent.Type.COMPLETE,
                    R.id.generic_progress,
                    0,
                    nbOK.get(),
                    nbKO.get(),
                    nbBooks
                )
            )
        }
    }

    private suspend fun mergeTo1(
        contentId: Long,
        nbOK: AtomicInteger,
        nbKO: AtomicInteger,
        nbBooks: Int
    ) {
        var success = false
        withContext(Dispatchers.IO) {
            val content = dao.selectContent(contentId)

            content?.let { c ->
                // Create book folder in Location 1
                val targetFolder = getOrCreateContentDownloadDir(
                    getApplication(),
                    c,
                    StorageLocation.PRIMARY_1,
                    false
                )
                if (targetFolder != null) {
                    // Transfer files
                    val sourceFolder =
                        getDocumentFromTreeUriString(getApplication(), c.storageUri)
                    if (sourceFolder != null) {
                        val files = listFiles(getApplication(), sourceFolder, null)
                        // TODO secondary progress for pages
                        files.forEach { it1 ->
                            val newUri = copyFile(
                                getApplication(),
                                it1.uri,
                                targetFolder.uri,
                                StringHelper.protect(it1.type),
                                StringHelper.protect(it1.name)
                            )
                            c.imageFiles?.forEach { it2 ->
                                if (it1.uri.toString() == it2.fileUri) {
                                    it2.fileUri = newUri.toString()
                                }
                            }
                        }
                        // Update Content Uris
                        c.storageDoc = targetFolder
                        dao.insertContentCore(c)
                        c.imageFiles?.let {
                            dao.insertImageFiles(it)
                        }
                        success = true

                        // Remove files from initial location
                        sourceFolder.delete()
                    }
                }
            }
            if (success) nbOK.incrementAndGet() else nbKO.incrementAndGet()

            EventBus.getDefault().post(
                ProcessEvent(
                    ProcessEvent.Type.PROGRESS,
                    R.id.generic_progress,
                    0,
                    nbOK.get(),
                    nbKO.get(),
                    nbBooks
                )
            )
        }
    }
}