package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.MeteoroApp
import com.example.network.MeteoroRepository
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read app_name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Meteoro Go", appName)
  }

  @Test
  fun `normalize meteoro custom scheme`() {
    val repo = MeteoroRepository()
    val normalized = repo.normalizeUrl("meteoro://my-app")
    assertEquals("https://api.meteoro.dev/app/my-app/code", normalized)
  }

  @Test
  fun `normalize meteoro app path`() {
    val repo = MeteoroRepository()
    val normalized = repo.normalizeUrl("meteoro://app/test1234/code")
    assertEquals("https://api.meteoro.dev/app/test1234/code", normalized)
  }

  @Test
  fun `preserve local development preview URL`() {
    val repo = MeteoroRepository()
    val previewUrl = "http://192.168.1.42:4173/app/code"
    assertEquals(previewUrl, repo.normalizeUrl(previewUrl))
  }

  @Test
  fun `preserve bundled standalone app URI`() {
    val repo = MeteoroRepository()
    assertEquals("project://bundle", repo.normalizeUrl("project://bundle"))
  }

  @Test
  fun `get built in demo coffee schema`() {
    val repo = MeteoroRepository()
    val demo = repo.getBuiltInDemo("coffee")
    assertEquals("Café Meteoro", demo.appName)
    assertNotNull(demo.screens)
    assertTrue(demo.screens!!.isNotEmpty())
  }

  @Test
  fun `parse bottom navigation items from app schema`() {
    val app = Moshi.Builder()
      .build()
      .adapter(MeteoroApp::class.java)
      .fromJson("""{"navigationBar":[{"route":"home","label":"Home"}]}""")

    assertEquals("home", app?.navigationBar?.single()?.route)
    assertEquals("Home", app?.navigationBar?.single()?.label)
  }
}
