package svcs

import com.github.stefanbirkner.systemlambda.SystemLambda.tapSystemOut
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
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

  @Nested
  inner class CommitTest {
    @Test
    fun `when no message is given, show an error`() {
      val output = tapSystemOut { commit(arrayOf()) }
      assertThat(output).isEqualTo("Message was not passed.\n")
    }

    @Test
    fun `when username is not configured, show an error`() {
      val output = tapSystemOut { commit(arrayOf("FIX")) }
      assertThat(output).isEqualTo("Please configure your name first.\nvcs config <name>\n")
    }

    @Test
    fun `when there is nothing to commit, show an error`() {
      config(arrayOf("Mat"))
      val output = tapSystemOut { commit(arrayOf("FIX")) }
      assertThat(output).isEqualTo("Nothing to commit.\n")
    }

    @Test
    fun `when there are staged files, show that the commit was successful`() {
      config(arrayOf("Mat"))
      val file = createTestFile(File("foo"), "abc".toByteArray())
      add(arrayOf(file.toString()))

      val output = tapSystemOut { commit(arrayOf("FIX")) }
      assertThat(output).isEqualTo("Changes are committed.\n")
    }
  }

  @Nested
  inner class LogTest {
    @Test
    fun `when the log is empty`() {
      val output = tapSystemOut { log(arrayOf()) }
      assertThat(output).isEqualTo("No commits yet.\n")
    }

    @Test
    fun `when there is a single log entry`() {
      val file = createTestFile(File("foo"), "abc".toByteArray())
      config(arrayOf("Mat"))
      add(arrayOf(file.toString()))
      commit(arrayOf("first commit"))

      val output = tapSystemOut { log(arrayOf()) }

      assertThat(output).matches("""^commit [0-9a-f]+\nAuthor: Mat\nfirst commit\n$""")
    }

    @Test
    fun `when there are multiple log entries`() {
      val file1 = createTestFile(File("foo"), "abc".toByteArray())
      val file2 = createTestFile(File("foo"), "abc".toByteArray())
      config(arrayOf("Mat"))
      add(arrayOf(file1.toString()))
      commit(arrayOf("first commit"))
      add(arrayOf(file2.toString()))
      commit(arrayOf("second commit"))

      val output = tapSystemOut { log(arrayOf()) }

      assertThat(output).matches("""^commit [0-9a-f]+\nAuthor: Mat\nsecond commit\n\ncommit [0-9a-f]+\nAuthor: Mat\nfirst commit\n$""")
    }
  }

  private fun createTestFile(relativePath: File, contents: ByteArray): File =
    File("testFiles").resolve(relativePath).also {
      it.writeBytes(contents)
    }
}
