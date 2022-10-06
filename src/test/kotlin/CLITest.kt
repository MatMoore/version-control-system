package svcs

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import java.io.File

private class CLITest {

  @BeforeEach
  fun cleanVcs() {
    File("vcs").deleteRecursively()
    File("testFiles").deleteRecursively()
    File("testFiles").mkdir()
    ensureVCSDirectoryExists()
  }

  @Nested
  inner class ConfigTest {
    @Test
    fun `when the config is empty, prompt the user`() {
      val output = tapSystemOut { config(arrayOf()) }
      assertThat(output).isEqualTo("Please, tell me who you are.\n")
    }

    @Test
    fun `when passing a username, echo back the username`() {
      val output = tapSystemOut { config(arrayOf("Mat")) }
      assertThat(output).isEqualTo("The username is Mat.\n")
    }

    @Test
    fun `when the config is set, print the username`() {
      config(arrayOf("Mat"))
      val output = tapSystemOut { config(arrayOf()) }
      assertThat(output).isEqualTo("The username is Mat.\n")
    }
  }

  @Nested
  inner class AddTest {
    @Test
    fun `when the index is empty, prompt to add files`() {
      val output = tapSystemOut { add(arrayOf()) }
      assertThat(output).isEqualTo("Add a file to the index.\n")
    }

    @Test
    fun `when adding a file, show that the file is tracked`() {
      val file = createTestFile(File("foo"), "abc".toByteArray())
      val output = tapSystemOut { add(arrayOf(file.toString())) }
      assertThat(output).isEqualTo("The file '${file}' is tracked.\n")
    }

    @Test
    fun `when a file doesn't exist, show an error`() {
      val output = tapSystemOut { add(arrayOf("testFiles/foo")) }
      assertThat(output).isEqualTo("Can't find 'testFiles/foo'.\n")
    }

    @Test
    fun `when files are tracked, list the tracked files`() {
      val file1 = createTestFile(File("foo"), "abc".toByteArray())
      val file2 = createTestFile(File("bar"), "abc".toByteArray())
      add(arrayOf(file1.toString()))
      add(arrayOf(file2.toString()))

      val output = tapSystemOut { add(arrayOf()) }
      assertThat(output).isEqualTo("Tracked files:\n${file2}\n${file1}\n")
    }
  }


  private fun createTestFile(relativePath: File, contents: ByteArray): File =
    File("testFiles").resolve(relativePath).also {
      it.writeBytes(contents)
    }
}
