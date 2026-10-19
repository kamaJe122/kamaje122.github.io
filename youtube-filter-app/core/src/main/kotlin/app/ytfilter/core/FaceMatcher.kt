package app.ytfilter.core

import kotlin.math.sqrt

/** A person to block, described by face embeddings computed from user-supplied reference photos. */
data class BlockedPerson(val name: String, val references: List<FloatArray>)

/**
 * Compares face embeddings found in a thumbnail against blocked people using cosine similarity.
 * Embeddings come from the on-device face model (e.g. MobileFaceNet); this class is model-agnostic.
 */
class FaceMatcher(people: List<BlockedPerson>, private val threshold: Float = DEFAULT_THRESHOLD) {
    private val people = people.map { p -> p.copy(references = p.references.map(::l2Normalize)) }

    val isEmpty: Boolean get() = people.all { it.references.isEmpty() }

    fun check(faces: List<FloatArray>): BlockReason.Face? {
        var best: BlockReason.Face? = null
        for (face in faces) {
            val f = l2Normalize(face)
            for (person in people) {
                for (ref in person.references) {
                    if (ref.size != f.size) continue
                    val sim = dot(f, ref)
                    if (sim >= threshold && (best == null || sim > best.similarity)) {
                        best = BlockReason.Face(person.name, sim)
                    }
                }
            }
        }
        return best
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.7f

        fun l2Normalize(v: FloatArray): FloatArray {
            val norm = sqrt(v.fold(0f) { acc, x -> acc + x * x })
            return if (norm == 0f) v.copyOf() else FloatArray(v.size) { v[it] / norm }
        }

        private fun dot(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s
        }
    }
}
