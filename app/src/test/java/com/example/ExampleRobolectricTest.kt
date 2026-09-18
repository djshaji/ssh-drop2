package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ServerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("SSHDrop", appName)
    }

    @Test
    fun `insert and query server entity`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AppDatabase.getDatabase(context)
        val dao = db.serverDao()

        val server = ServerEntity(
            name = "Test Server",
            host = "192.168.1.50",
            port = 22,
            username = "ubuntu",
            authType = "PASSWORD",
            password = "secret"
        )
        val id = dao.insertServer(server)
        val loaded = dao.getServerById(id)

        assertNotNull(loaded)
        assertEquals("Test Server", loaded?.name)
        assertEquals("192.168.1.50", loaded?.host)
        assertEquals("ubuntu", loaded?.username)
    }

    @Test
    fun `remote item symlink properties verification`() {
        val symlinkItem = com.example.ssh.RemoteItem(
            name = "current_build",
            path = "/var/www/current_build",
            isDirectory = true,
            size = 0L,
            permissions = "lrwxrwxrwx",
            lastModified = 1700000000000L,
            isSymlink = true,
            symlinkTarget = "/var/www/releases/v2.1.0"
        )

        assertEquals(true, symlinkItem.isSymlink)
        assertEquals(true, symlinkItem.isDirectory)
        assertEquals("/var/www/releases/v2.1.0", symlinkItem.symlinkTarget)
        assertEquals("lrwxrwxrwx", symlinkItem.permissions)
    }
}
