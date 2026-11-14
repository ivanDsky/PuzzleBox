package com.ivandsky.puzzlebox.data.cloud

/**
 * What an account's first restore brought to this device: rows pulled per kind name, only for kinds that brought
 * some. The engine names no kind; the app shell decides which kinds the welcome counts and how it words them.
 */
data class RestoreSummary(val countsByKind: Map<String, Int>)
