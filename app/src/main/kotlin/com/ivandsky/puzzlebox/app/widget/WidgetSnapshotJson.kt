package com.ivandsky.puzzlebox.app.widget

import com.ivandsky.puzzlebox.domain.TodayStatus
import com.ivandsky.puzzlebox.model.GameId
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * The widget's cached picture as JSON, through the platform's org.json so the cache adds no dependency. Only this
 * device reads it back; [decode] gives null for anything it cannot read, including another [VERSION], and the widget
 * then draws the empty state until the next good read writes a new one.
 */
internal object WidgetSnapshotJson {
    private const val VERSION = 1

    fun encode(snapshot: WidgetSnapshot): String {
        val state = snapshot.state
        return JSONObject()
            .put("version", VERSION)
            .put("day", snapshot.day.toString())
            .put("rings", JSONArray(state.rings.map { ring(it) }))
            .put("streak", JSONObject().put("days", state.streak.days).put("best", state.streak.best).putOpt("freezes", state.streak.freezesHeld))
            .put("upNext", upNext(state.upNext))
            .putOpt("rollover", state.nextRollover?.toEpochMilli())
            .toString()
    }

    fun decode(text: String): WidgetSnapshot? = try {
        val json = JSONObject(text)
        if (json.optInt("version") != VERSION) {
            null
        } else {
            val rings = json.getJSONArray("rings")
            val streak = json.getJSONObject("streak")
            WidgetSnapshot(
                day = LocalDate.parse(json.getString("day")),
                state = WidgetState(
                    rings = List(rings.length()) { ring(rings.getJSONObject(it)) },
                    streak = WidgetStreak(
                        days = streak.getInt("days"),
                        best = streak.getInt("best"),
                        freezesHeld = if (streak.has("freezes")) streak.getInt("freezes") else null,
                    ),
                    upNext = upNext(json.getJSONObject("upNext")),
                    nextRollover = if (json.has("rollover")) Instant.ofEpochMilli(json.getLong("rollover")) else null,
                ),
            )
        }
    } catch (e: JSONException) {
        null
    } catch (e: DateTimeException) {
        null
    } catch (e: IllegalStateException) {
        // A status, rank or up next kind this version does not know.
        null
    }

    private fun ring(ring: WidgetRing): JSONObject {
        val json = JSONObject()
            .put("game", ring.game.value)
            .put("name", ring.displayName)
            .putOpt("bestMs", ring.bestMs)
            .put("newBest", ring.newBest)
        when (val status = ring.status) {
            TodayStatus.NotStarted -> json.put("status", "notStarted")
            is TodayStatus.InProgress -> json.put("status", "inProgress").put("statusMs", status.elapsedMs)
            is TodayStatus.Solved -> json.put("status", "solved").put("statusMs", status.timeMs)
            TodayStatus.Offline -> json.put("status", "offline")
        }
        when (val rank = ring.rank) {
            is WidgetRank.Ranked -> json.put("rank", "ranked").put("position", rank.position).put("count", rank.count)
            WidgetRank.Missing -> json.put("rank", "missing")
            WidgetRank.SignedOut -> json.put("rank", "signedOut")
        }
        return json
    }

    private fun ring(json: JSONObject) = WidgetRing(
        game = GameId(json.getString("game")),
        displayName = json.getString("name"),
        status = when (val status = json.getString("status")) {
            "notStarted" -> TodayStatus.NotStarted
            "inProgress" -> TodayStatus.InProgress(elapsedMs = json.getLong("statusMs"))
            "solved" -> TodayStatus.Solved(timeMs = json.getLong("statusMs"))
            "offline" -> TodayStatus.Offline
            else -> error("unknown status $status")
        },
        bestMs = if (json.has("bestMs")) json.getLong("bestMs") else null,
        rank = when (val rank = json.getString("rank")) {
            "ranked" -> WidgetRank.Ranked(position = json.getInt("position"), count = json.getInt("count"))
            "missing" -> WidgetRank.Missing
            "signedOut" -> WidgetRank.SignedOut
            else -> error("unknown rank $rank")
        },
        newBest = json.getBoolean("newBest"),
    )

    private fun upNext(upNext: WidgetUpNext): JSONObject = when (upNext) {
        is WidgetUpNext.Play -> JSONObject().put("kind", "play").put("game", upNext.game.value).put("name", upNext.gameName)
        WidgetUpNext.Done -> JSONObject().put("kind", "done")
        WidgetUpNext.Unknown -> JSONObject().put("kind", "unknown")
    }

    private fun upNext(json: JSONObject): WidgetUpNext = when (val kind = json.getString("kind")) {
        "play" -> WidgetUpNext.Play(GameId(json.getString("game")), json.getString("name"))
        "done" -> WidgetUpNext.Done
        "unknown" -> WidgetUpNext.Unknown
        else -> error("unknown up next $kind")
    }
}
