package svcs

import kotlin.system.exitProcess

val helpMessages = mapOf(
  "config" to "Get and set a username.",
  "add" to "Add a file to the index.",
  "log" to "Show commit logs.",
  "commit" to "Save changes.",
  "checkout" to "Restore a file."
)

fun printHelp() {
  println("These are SVCS commands:")
  for((name, desc) in helpMessages.entries) {
    println("%-11s %s".format(name, desc))
  }
}

fun main(args: Array<String>) {
  if (args.size != 1) {
    printHelp()
    exitProcess(0)
  }

  when (val command = args[0]) {
    "--help" -> {
      printHelp()
    }

    in helpMessages -> {
      println(helpMessages[command])
    }

    else -> {
      println("'$command' is not a SVCS command.")
    }
  }
}
