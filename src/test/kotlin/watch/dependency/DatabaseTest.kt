package watch.dependency

import com.google.common.jimfs.Configuration.unix
import com.google.common.jimfs.Jimfs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class DatabaseTest(
	@Suppress("unused") // Used in JUnit runner.
	private val name: String,
	private val database: Database,
) {
	@Test fun returnsTrueAfterMark() {
		val exampleCoordinates = MavenCoordinate("com.example", "example")

		assertFalse(database.coordinateSeen(exampleCoordinates))
		assertFalse(database.coordinateVersionSeen(exampleCoordinates, "1.0.0"))

		database.markCoordinateVersionSeen(exampleCoordinates, "1.0.0")
		assertTrue(database.coordinateSeen(exampleCoordinates))
		assertTrue(database.coordinateVersionSeen(exampleCoordinates, "1.0.0"))
	}

	@Test fun markingSeenIsIdempotent() {
		val exampleCoordinates = MavenCoordinate("com.example", "example")

		database.markCoordinateVersionSeen(exampleCoordinates, "1.0.0")
		assertTrue(database.coordinateSeen(exampleCoordinates))
		assertTrue(database.coordinateVersionSeen(exampleCoordinates, "1.0.0"))

		database.markCoordinateVersionSeen(exampleCoordinates, "1.0.0")
		assertTrue(database.coordinateSeen(exampleCoordinates))
		assertTrue(database.coordinateVersionSeen(exampleCoordinates, "1.0.0"))
	}

	companion object {
		@JvmStatic
		@Parameters(name = "{0}")
		fun data() = listOf(
			arrayOf("memory", InMemoryDatabase()),
			arrayOf("fs", FileSystemDatabase(Jimfs.newFileSystem(unix()).rootDirectory))
		)
	}
}
