import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.server.testing.*
import tim.kiko.Flat
import tim.kiko.main
import kotlin.test.*

class ServerTest {
    val gson = Gson()

    @Test
    fun testRequests() = withTestApplication(Application::main) {
        with(handleRequest(HttpMethod.Get, "/flats/1")) {
            assertEquals(HttpStatusCode.OK, response.status())
            val resp = gson.fromJson(response.content, Flat::class.java)
            assertEquals(resp, Flat(1, "address 1", 11))
        }
    }

    //And so on. Sorry, don't have time to create all tests
}