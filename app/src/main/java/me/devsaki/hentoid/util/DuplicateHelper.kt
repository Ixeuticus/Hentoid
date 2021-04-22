package me.devsaki.hentoid.util

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.util.Consumer
import info.debatty.java.stringsimilarity.Cosine
import info.debatty.java.stringsimilarity.interfaces.StringSimilarity
import io.reactivex.ObservableEmitter
import me.devsaki.hentoid.database.CollectionDAO
import me.devsaki.hentoid.database.DuplicatesDAO
import me.devsaki.hentoid.database.domains.Content
import me.devsaki.hentoid.database.domains.DuplicateEntry
import me.devsaki.hentoid.enums.AttributeType
import me.devsaki.hentoid.enums.StatusContent
import me.devsaki.hentoid.workers.DuplicateDetectorWorker
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class DuplicateHelper {

    companion object {
        // Thresholds according to the "sensibility" setting
        private val COVER_THRESHOLDS = doubleArrayOf(0.71, 0.75, 0.8) // @48-bit resolution, according to calibration tests
        private val TEXT_THRESHOLDS = doubleArrayOf(0.8, 0.85, 0.9)
        private val TOTAL_THRESHOLDS = doubleArrayOf(0.8, 0.85, 0.9)

        /**
         * Detect if there are missing cover hashes
         */
        fun indexCovers(
                context: Context,
                dao: CollectionDAO,
                library: List<Content>,
                interrupted: AtomicBoolean,
                emitter: ObservableEmitter<Pair<Int, Float>>) {
            Helper.assertNonUiThread()
            val noCoverHashes = library.filter { 0L == it.cover.imageHash && !it.cover.status.equals(StatusContent.ONLINE) }
            if (noCoverHashes.isNotEmpty()) {
                val hash = ImagePHash(48, 8)
                var elementsKO = 0
                for ((progress, content) in noCoverHashes.withIndex()) {
                    if (interrupted.get()) break
                    try {
                        FileHelper.getInputStream(context, Uri.parse(content.cover.fileUri))
                                .use {
                                    val b = BitmapFactory.decodeStream(it)
                                    content.cover.imageHash = hash.calcPHash(b)
                                }
                    } catch (e: IOException) {
                        content.cover.imageHash = Long.MIN_VALUE
                        elementsKO++
                        Timber.w(e) // Doesn't break the loop
                    } finally {
                        // Update the picture in DB
                        dao.insertImageFile(content.cover)
                        // Update the book JSON
                        if (content.jsonUri.isNotEmpty()) ContentHelper.updateContentJson(context, content)
                        else ContentHelper.createContentJson(context, content)
                    }

                    emitter.onNext(Pair(DuplicateDetectorWorker.STEP_COVER_INDEX, (progress + 1) * 1f / noCoverHashes.size))
                    Timber.i("Calculating hashes : %s / %s", progress + 1, noCoverHashes.size)
                }
                emitter.onNext(Pair(DuplicateDetectorWorker.STEP_COVER_INDEX, 1f))
                emitter.onComplete()
            }
        }

        fun processLibrary(
                duplicatesDao: DuplicatesDAO,
                library: List<Content>,
                useTitle: Boolean,
                useCover: Boolean,
                useArtist: Boolean,
                sameLanguageOnly: Boolean,
                sensitivity: Int,
                interrupted: AtomicBoolean,
                progress: Consumer<Float>
        ) {
            Helper.assertNonUiThread()

            val detectedDuplicatesHash = HashSet<Pair<Int, Int>>()
            val fullLines = HashSet<Int>()
            val nbCombinations = (library.size * (library.size - 1)) / 2
            var lineMatchCounter = 0

            val textComparator = Cosine()
            var globalProgress = 0f

            var contentRef: Content
            var contentCandidate: Content

            var referenceTitleDigits = ""
            var referenceTitle = ""

            var titleScore: Float
            var coverScore: Float
            var artistScore: Float

            val tempResults = ArrayList<DuplicateEntry>()

            do {
                for (i in 0 until library.size - 1) {
                    if (interrupted.get()) return
                    if (fullLines.contains(i)) continue

                    lineMatchCounter = 0
                    contentRef = library[i]
                    if (useTitle) {
                        referenceTitleDigits = StringHelper.cleanup(contentRef.title)
                        referenceTitle = StringHelper.removeDigits(referenceTitleDigits)
                    }

                    for (j in (i + 1) until library.size) {
                        if (interrupted.get()) return

                        // Check if that combination has already been processed
                        if (detectedDuplicatesHash.contains(Pair(i, j))) {
                            lineMatchCounter++
                            continue
                        }

                        // Process current combination of Content
                        titleScore = -1f
                        coverScore = -1f
                        artistScore = -1f

                        contentCandidate = library[j]

                        // Remove if not same language
                        if (sameLanguageOnly && !containsSameLanguage(contentRef, contentCandidate)) {
                            detectedDuplicatesHash.add(Pair(i, j))
                            continue
                        }

                        if (useCover) {
                            // Don't analyze anything if covers have not been hashed (will be done on next iteration)
                            if (0L == contentRef.cover.imageHash || 0L == contentCandidate.cover.imageHash) continue
                            // Ignore unhashable covers
                            coverScore = if (Long.MIN_VALUE == contentRef.cover.imageHash || Long.MIN_VALUE == contentCandidate.cover.imageHash) {
                                -1f
                            } else {
                                val preCoverScore = ImagePHash.similarity(contentRef.cover.imageHash, contentCandidate.cover.imageHash)
                                if (preCoverScore >= COVER_THRESHOLDS[sensitivity]) preCoverScore else 0f
                            }
                        }

                        if (useTitle) titleScore = computeTitleScore(textComparator, referenceTitleDigits, referenceTitle, contentCandidate, sensitivity)

                        if (useArtist) artistScore = computeArtistScore(contentRef, contentCandidate)

                        val duplicateResult = DuplicateEntry(contentRef.id, contentRef.size, contentCandidate.id, titleScore, coverScore, artistScore)
                        if (duplicateResult.calcTotalScore() >= TOTAL_THRESHOLDS[sensitivity]) tempResults.add(duplicateResult)
                        detectedDuplicatesHash.add(Pair(i, j))
                    }

                    // Record full lines for quicker scan
                    if (lineMatchCounter == library.size - i - 1) fullLines.add(i)

                    // Save results for this reference
                    if (tempResults.isNotEmpty()) {
                        duplicatesDao.insertEntries(tempResults)
                        tempResults.clear()
                    }

                    globalProgress = detectedDuplicatesHash.size * 1f / nbCombinations
                    Timber.i(" >> PROCESS [%s] %s / %s (%s %%)", i, detectedDuplicatesHash.size, nbCombinations, globalProgress)
                    progress.accept(globalProgress)
                }
                Timber.i(" >> PROCESS End reached")
            } while (globalProgress < 1f)

            progress.accept(1f)
            detectedDuplicatesHash.clear()
        }

        private fun containsSameLanguage(contentRef: Content, contentCandidate: Content): Boolean {
            val refLanguages = contentRef.attributeMap[AttributeType.LANGUAGE]
            val candidateLanguages = contentCandidate.attributeMap[AttributeType.LANGUAGE]
            if (!candidateLanguages.isNullOrEmpty() && !refLanguages.isNullOrEmpty()) {
                val candidateCodes = candidateLanguages.map { LanguageHelper.getCountryCodeFromLanguage(it.name) }
                val refCodes = refLanguages.map { LanguageHelper.getCountryCodeFromLanguage(it.name) }

                for (refCode in refCodes) {
                    if (candidateCodes.contains(refCode)) return true
                }
                return false
            }
            return true
        }

        private fun computeTitleScore(
                textComparator: StringSimilarity,
                referenceTitleDigits: String,
                referenceTitle: String,
                contentCandidate: Content,
                sensitivity: Int
        ): Float {
            var candidateTitle = StringHelper.cleanup(contentCandidate.title)
            val similarity1 = textComparator.similarity(referenceTitleDigits, candidateTitle).toFloat()
            return if (similarity1 > TEXT_THRESHOLDS[sensitivity]) {
                candidateTitle = StringHelper.removeDigits(candidateTitle)
                val similarity2 = textComparator.similarity(referenceTitle, candidateTitle)
                if (similarity2 - similarity1 < 0.02) {
                    similarity1
                } else {
                    0f // Most probably a chapter variant -> set to 0%
                }
            } else {
                0f // Below threshold
            }
        }

        private fun computeArtistScore(
                contentReference: Content,
                contentCandidate: Content
        ): Float {
            val refArtists = contentReference.attributeMap[AttributeType.ARTIST]
            val candidateArtists = contentCandidate.attributeMap[AttributeType.ARTIST]
            if (!candidateArtists.isNullOrEmpty() && !refArtists.isNullOrEmpty()) {
                for (candidateArtist in candidateArtists) {
                    for (refArtist in refArtists) {
                        if (candidateArtist.id == refArtist.id) return 1f
                        if (refArtist.equals(candidateArtist)) return 1f
                        if (StringHelper.isTransposition(refArtist.name, candidateArtist.name)) return 1f
                    }
                }
                return 0f // No match
            }
            return -1f // Nothing to match against
        }
    }
}