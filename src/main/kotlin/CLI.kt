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
  "log" to ::log,
  "checkout" to ::checkout
)

data class LogEntry(val author: String, val commitHash: String, val message: String)
data class Diff(val fileDiffs: Map<File,CommitHash>)

sealed interface CommitRef {
  companion object {
    fun fromString(string: String): CommitRef =
      // TODO: it would be nice to also support relative hashes
      // e.g. next, prev
      // if we support branching, could have one for the branch point as well
      when(string.lowercase()) {
        "latest" -> {
          Latest
        }
        else -> {
          // TODO: this could check it looks like a hash first
          string.toCommitHash()
        }
      }
  }
}
object Latest: CommitRef

@JvmInline
value class CommitHash(private val s: CharSequence) : CommitRef, CharSequence by s {
  override fun toString(): String {
    return s.toString()
  }
}

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
 * FIXME: this needs to handle deletions
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

  requireLatestVersion()

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
  saveHead(commitHash.toCommitHash())
  println("Changes are committed.")
}

/**
 * Restore files from an earlier commit.
 * This command performs the minimal changes to the working tree
 * to recreate the commit, so if you have uncommited changes to a file,
 * and you revert to an earlier commit which has the same version of the file as the one you started from,
 * then your uncommited changes will still be there.
 * This means that checking out the current ("head") commit is always a no-op.
 * We currently don't have a command that fully reverts the working tree to the exact state
 * of a commit, like `git reset --hard` or `git stash`
 */
fun checkout(args: Array<String>) {
  if(args.isEmpty()) {
    println("Commit id was not passed.")
    exitProcess(1)
  }

  val head: CommitHash = getHead()

  // TODO tidy this up
  val commitRef = runCatching { CommitRef.fromString(args.first()) }.recover {
    println("Commit does not exist.")
    exitProcess(1)
  }.getOrThrow()

  val commitHash = runCatching {  resolveCommitRef(commitRef) }.recover {
    println("Commit does not exist.")
    exitProcess(1)
  }.getOrThrow()

  val diff = getDiff(head, commitHash)
  if(diff == null) {
    println("Log file is corrupt T_T")
    exitProcess(1)
  }

  applyDiff(diff)
  saveHead(commitHash)
  println("Switched to commit $commitHash.")
}

fun resolveCommitRef(commitRef: CommitRef): CommitHash =
  when(commitRef) {
    Latest -> {
      latestCommitHash() ?: throw IllegalArgumentException("the log is empty, so there is no latest commit")
    }
    is CommitHash -> commitRef
  }

fun CharSequence.toCommitHash(): CommitHash {
  if(!(loadLog().any { it.commitHash == this })) {
    throw Exception("Commit does not exist")
  }
  return CommitHash(this)
}

fun getHead(): CommitHash {
  val headFile = File("vcs/head.txt")
  return CommitHash(headFile.readText().trim())
}

/**
 * Generate a diff between two commits. A diff contains the information required
 * to modify the working tree from one commit to another.
 *
 * We construct this by iterating through the log from the `from` commit to the `to` commit,
 * keeping track of all the files that have changed. Then we return the copy of the file
 * that is closest to the `to` commit.
 *
 * Returns null if the log file is corrupt (i.e. the commit hash does not exist in the log)
 * Returns an empty diff if the `from` and `to` commit are the same.
 */
fun getDiff(from: CommitHash, to: CommitHash): Diff? {
  if(from == to) {
    return Diff(mapOf())
  }

  var log = loadLog()
  var fromIndex = log.indexOfFirst { it.commitHash.toCommitHash() == from }
  var toIndex = log.indexOfFirst { it.commitHash.toCommitHash() == to }
  val fileDiffs: MutableMap<File, CommitHash> = mutableMapOf()

  if(fromIndex == -1 || toIndex == -1) {
    return null
  }

  if(fromIndex > toIndex) {
    log = log.reversed()
    fromIndex = log.indexOfFirst { it.commitHash.toCommitHash() == from }
    toIndex = log.indexOfFirst { it.commitHash.toCommitHash() == to }
  }

  for (entry in log.subList(fromIndex + 1, toIndex + 1)) {
    val entryHash = entry.commitHash.toCommitHash()
    val filesChanged = getCommitFiles(entryHash)

    for (file in filesChanged) {
      fileDiffs[file] = entryHash
    }
  }

  return Diff(fileDiffs)
}

/**
 * Apply a diff in order to return the working tree to the state of another commit
 * This overwrites any uncommited changes in the working tree, but leaves intact
 * any files that are not included in the diff (i.e. they are the same in both commits).
 */
fun applyDiff(diff: Diff) {
  for((file, commitHash) in diff.fileDiffs.entries) {
    val commitPath = File("vcs/commits/${commitHash}")
    val sourceFile = commitPath.resolve(file)
    sourceFile.copyTo(file, overwrite = true)
  }
}

fun getCommitFiles(commit: CommitHash): List<File> {
  val commitPath = File("vcs/commits/${commit}")
  return File("vcs/commits/${commit}").walkTopDown().filter(File::isFile).map { it.relativeTo(commitPath) }.toList()
}

/**
 * Check that the head pointer is the latest version in the log.
 * If not, then we have an older version checked out, and should not
 * allow further commits.
 * This is equivalent to a "detached head" in git
 */
fun requireLatestVersion() {
  val latestHash = latestCommitHash()
  if(latestHash != null && latestHash != getHead()) {
    println("You need to checkout the latest version (${latestHash}) before you can commit new changes")
    exitProcess(1)
  }
}

fun latestCommitHash(): CommitHash? {
  val log = loadLog()
  if(log.isEmpty()) {
    return null
  }
  val latestEntryInLog = loadLog().last()
  return latestEntryInLog.commitHash.toCommitHash()
}

fun saveHead(head: CommitHash) {
  val headFile = File("vcs/head.txt")
  headFile.writeText(head.toString())
}

/**
 * TODO: this should integrate with a pager
 */
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
