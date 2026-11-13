package com.ivandsky.puzzlebox.data.cloud.kinds

/**
 * The `formatVersion` of the drafts, published and received docs: the one value `firestore.rules` accepts for a space
 * doc. New optional fields do not bump it; a doc with another version is not read.
 */
internal const val CLOUD_FORMAT_VERSION = 1L
