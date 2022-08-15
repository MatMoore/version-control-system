package svcs

import java.io.File
import kotlin.system.exitProcess

val helpMessages = mapOf(
  "config" to "Get and set a username.",
  "add" to "Add a file to the index.",
  "log" to "Show commit logs.",
  "commit" to "Save changes.",
  "checkout" to "Restore a file."
)

val commands = mapOf("config" to ::config, "add" to ::add)

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
  val index = loadIndex().toMutableSet()

  if(args.isEmpty()) {
    if (index.isEmpty()) {
      println("Add a file to the index.")
    } else {
      println("Tracked files:")
      index.forEach { println(it) }
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
    saveIndex(index)
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

fun loadIndex(): Set<String> {
  val indexFile = File("vcs/index.txt")
  if (!indexFile.exists()) {
    return setOf()
  }

  return indexFile.readText().split("\n").toSet()
}

fun saveIndex(index: Set<String>) {
  val indexFile = File("vcs/index.txt")
  val encoded = index.joinToString("\n")
  indexFile.writeText(encoded)
}

fun loadName(): String? {
  val config = loadConfig() ?: return null
  return config["name"]
}

fun ensureVCSDirectoryExists() {
  File("vcs").mkdirs()
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