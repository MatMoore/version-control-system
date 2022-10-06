# Version control system

A toy version control system.

This started as a practice project to learn Kotlin, based on https://hyperskill.org/projects/177

## Installation
- TODO

## Notable differences from git
1. There is no selective committing of tracked files. Files can be either tracked or untracked. 
    When you commit, all tracked files are committed.
2. There is no status command.
3. The add command returns a list of tracked files when called with no arguments

## Implemented commands

- config
- add
- commit
- log
- checkout

## Bugs

- you can commit twice without changing anything
- checkout breaks if there is no HEAD file

## Weirdness

- after a checkout, you can have files left over from a later commit
- you can see tracked files, but not untracked files

## Missing functionality

- removing files
- pager for log
- timestamps for commits
- branching, merging, rebasing
- reverting, amending

## Other ideas

- Predict conflicts before overwriting working tree with conflicting files