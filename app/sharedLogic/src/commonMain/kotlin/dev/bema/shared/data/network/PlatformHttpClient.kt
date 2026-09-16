package dev.bema.shared.data.network

import io.ktor.client.HttpClient

/** Creates an engine-backed client in the platform source set. */
expect fun createPlatformHttpClient(): HttpClient
