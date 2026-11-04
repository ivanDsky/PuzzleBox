// Pure-Kotlin network module: OkHttp/Retrofit, the anonymous LinkedIn session and the one
// generic FeedTransport every game's DailySource rides. Nothing here needs Android.
plugins {
    alias(libs.plugins.puzzlebox.jvm.library)
}

dependencies {
    // FeedTransport/FeedRequest/FetchResult: the contract LinkedInFeedTransport implements.
    implementation(projects.core.game)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.koin.core)

    testImplementation(libs.okhttp.mockwebserver)
}
