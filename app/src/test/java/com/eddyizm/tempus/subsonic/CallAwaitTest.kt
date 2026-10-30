package com.eddyizm.tempus.subsonic

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.mockito.kotlin.mock
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

class CallAwaitTest {

    @Test
    fun `awaitOrNull gives the response when the request succeeds`() = runBlocking {
        val response = Response.success("ok")
        val call = mock<Call<String>>()
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as Callback<String>).onResponse(call, response)
            null
        }.whenever(call).enqueue(any())

        assertEquals(response, call.awaitOrNull())
    }

    @Test
    fun `awaitOrNull gives null when the request fails`() = runBlocking {
        val call = mock<Call<String>>()
        doAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            (invocation.arguments[0] as Callback<String>).onFailure(call, IOException("offline"))
            null
        }.whenever(call).enqueue(any())

        assertNull(call.awaitOrNull())
    }
}
