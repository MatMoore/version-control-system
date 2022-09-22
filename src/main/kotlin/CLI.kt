package svcs

import java.io.File
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.system.exitProcess

val helpMessages = mapOf(
  "config" to "Get and set a username.",
  "add" to "Add a file to the index.",
  "log" to "Show commit logs.",
  "commit" to "Save changes.",
  "checkout" to "Restore a file."
)

val commands = mapOf(
  "config" to ::config,
  "add" to ::add,
  "commit" to ::commit,
  "log" to ::log
)

data class LogEntry(val author: String, val commitHash: String, val message: String)

@JvmInline
value class CommitHash(private val s: CharSequence)

fun printHelp() {
  println("These are SVCS commands:")
  for((name, desc) in helpMessages.entries) {
    println("%-11s %s".format(name, desc))
  }
}

fun printCommandHelp(name: String) {
  println("%-11s %s".format(name, helpMessages[name]!!))
}

fun config(args: Array<String>) {
  if(args.isEmpty()) {
    val name = loadName()
    if (name === null) {
      println("Please, tell me who you are.")
    } else {
      println("The username is $name.")
    }
  } else {
    val name = args[0]
    storeConfig(mapOf("name" to name))
    println("The username is $name.")
  }
}

fun add(args: Array<String>) {
  val index = Index.load()

  if(args.isEmpty()) {
    if (index.isEmpty()) {
      println("Add a file to the index.")
    } else {
      println("Tracked files:")
      for (trackedFile in index.trackedFiles()) {
        println(trackedFile)
      }
    }
  } else {
    args.forEach {
      if(!File(it).exists()) {
        println("Can't find '$it'.")
        exitProcess(1)
      }
      index.add(it)
      println("The file '$it' is tracked.")
    }
    index.save()
  }
}


/**
 * Commit the contents of the index to the repository by
 * copying files to a subfolder
 * Note that this doesn't do any validation of the index
 * and assumes that no paths begin with '../'
 */
fun commit(args: Array<String>) {
  if(args.isEmpty()) {
    println("Message was not passed.")
    exitProcess(1)
  }

  val message = args.first()
  val author = getAuthor()

  if(author == null) {
    println("Please configure your name first.")
    printCommandHelp("config")
    exitProcess(1)
  }

  val index = Index.load()
  val stagedFiles = index.stagedFiles()
  if(stagedFiles.isEmpty()) {
    println("Nothing to commit.")
    exitProcess(1)
  }
  val commitHash = generateCommitHash(stagedFiles, message)

  for (trackedFile in index.trackedFiles()) {
    val origin = File(trackedFile)
    val destination = File("vcs/commits/${commitHash}/${trackedFile}")
    destination.parentFile.mkdirs()
    origin.copyTo(destination)

    val fileHash = generateFileHash(File(trackedFile))
    index.updateVersion(trackedFile, fileHash)
  }

  index.save()
  appendToLog(commitHash, message, author)
  println("Changes are committed.")
}

fun checkout(args: Array<String>) {
  if(args.isEmpty()) {
    println("Commit id was not passed.")
    exitProcess(1)
  }

  val head: CommitHash = getHead()
  val checkoutHashStr = args.first()!!
  val checkoutHash = runCatching { checkoutHashStr.toCommitHash()}.recover {
    println("Commit does not exist")
    exitProcess(1)
  }

  //val diff: Diff = getDiff(head, checkoutHash)
  //applyDiff(diff)
  //setHead(checkoutHash)
}

// TODO: Validate the commit first
fun CharSequence.toCommitHash(): CommitHash {
  return CommitHash(this)
}

/**
 * TODO: this should read head from a file
 */
fun getHead(): CommitHash {
  val latestEntryInLog = loadLog().last()
  return latestEntryInLog.commitHash.toCommitHash()
}

fun log(args: Array<String>) {
  val logEntries = loadLog().reversed()
  if (logEntries.isEmpty()) {
    println("No commits yet.")
    return
  }

  for (entry in logEntries.dropLast(1)) {
    println("commit ${entry.commitHash}")
    println("Author: ${entry.author}")
    println(entry.message)
    println()
  }

  val last = logEntries.last()
  println("commit ${last.commitHash}")
  println("Author: ${last.author}")
  println(last.message)
}

/**
 * Generate a commit hash based on the hash of the changed file contents,
 * their filenames, and the commit message.
 * FIXME: this will create collisions if we make the same commit twice - this
 * case is not handled
 */
fun generateCommitHash(filenames: Set<String>, message: String): String {
  val sha256 = MessageDigest.getInstance("SHA-256")
  sha256.update(message.toByteArray())

  for (filename in filenames.sorted()) {
    val file = File(filename)
    sha256.update(filename.toByteArray())
    sha256.update(file.inputStream().readAllBytes())
  }

  return "%032x".format(BigInteger(1, sha256.digest()))
}

fun generateFileHash(file: File): String {
  val sha256 = MessageDigest.getInstance("SHA-256")
  sha256.update(file.inputStream().readAllBytes())

  return "%032x".format(BigInteger(1, sha256.digest()))
}

fun appendToLog(commitHash: String, message: String, author: String) {
  val logFile = File("vcs/log.txt")
  val formattedLog = "${commitHash} | ${author}: ${message}\n"
  logFile.appendText(formattedLog)
}

fun loadLog() : List<LogEntry> {
  val logFile = File("vcs/log.txt")

  if (!logFile.exists()) {
    return listOf()
  }

  val contents = logFile.readText().trimEnd()
  if(contents == "") {
    return listOf()
  }

  val logRegex = Regex("^(?<commitHash>[a-f\\d]+) \\| (?<author>[^:]+): (?<message>.*)")

  return contents.split("\n").map {
    val matchResult = logRegex.matchEntire(it) ?: throw Exception("Log file corrupted")
    LogEntry(
      matchResult.groups["author"]!!.value,
      matchResult.groups["commitHash"]!!.value,
      matchResult.groups["message"]!!.value
    )
  }
}

fun storeConfig(values: Map<String, String>) {
  val encoded = values.map { "${it.key}: ${it.value}" }.joinToString("\n") + "\n"
  val configFile = File("vcs/config.txt")
  configFile.writeText(encoded)
}

fun loadConfig(): Map<String, String>? {
  val configFile = File("vcs/config.txt")
  if (!configFile.exists()) {
    return null
  }

  return configFile.readText().split("\n").associate {
    val entry = it.split(": ")
    entry.first() to entry.last()
  }
}

fun getAuthor() : String? = loadConfig()?.get("name")

/**
 * The index stores the tracked files.
 * We also store the hash of the last committed version of the file,
 * so we can detect when there are changes that can be committed.
 *
 * FIXME - this has a weird constructor
 * FIXME - use File instead of string?
 */
class Index(val fileEntries: MutableMap<String, String>) {
  companion object {
    fun load(): Index {
      val indexFile = File("vcs/index.txt")
      if (!indexFile.exists()) {
        return Index(mutableMapOf())
      }

      val indexText = indexFile.readText().trimEnd()

      val fileEntries = indexText.split("\n").associate {
        val parts = it.split(":")
        parts.first() to parts.last()
      }

      return Index(fileEntries.toMutableMap())
    }
  }

  private fun hasChanged(filename: String, committedHash: String): Boolean {
    val file = File(filename)
    val currentHash = generateFileHash(file)
    return currentHash != committedHash
  }

  fun stagedFiles(): Set<String> = fileEntries.entries.filter { entry -> hasChanged(entry.key, entry.value) }.map { it.key }.toSet()

  fun add(file: String) {
    fileEntries[file] = ""
  }

  fun isEmpty(): Boolean {
    return fileEntries.isEmpty()
  }

  fun trackedFiles(): Set<String> = fileEntries.keys.toSet()

  fun updateVersion(file: String, fileHash: String) {
    fileEntries[file] = fileHash
  }

  fun save() {
    val indexFile = File("vcs/index.txt")
    val encoded = fileEntries.map { fileDetails -> "${fileDetails.key}:${fileDetails.value}"}.joinToString("\n")
    indexFile.writeText(encoded)
  }
}

fun loadName(): String? {
  val config = loadConfig() ?: return null
  return config["name"]
}

fun ensureVCSDirectoryExists() {
  File("vcs/commits").mkdirs()
}

fun main(args: Array<String>) {
  if (args.isEmpty()) {
    printHelp()
    exitProcess(0)
  }

  ensureVCSDirectoryExists()

  when (val command = args[0]) {
    "--help" -> {
      printHelp()
    }

    in commands -> {
      commands[command]?.invoke(args.drop(1).toTypedArray())
    }

    in helpMessages -> {
      println(helpMessages[command])
    }

    else -> {
      println("'$command' is not a SVCS command.")
    }
  }
}
