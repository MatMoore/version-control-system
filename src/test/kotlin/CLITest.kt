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

  private fun createTestFile(relativePath: File, contents: ByteArray) {
    File("testFiles").resolve(relativePath).writeBytes(contents)
  }

}
