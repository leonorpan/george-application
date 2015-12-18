# George Client - jvm source

This is main code is dirstibuted in George (the native client)


## Gradle

This project uses Gradle.

If you have Gradle installed, then excellent.

If you don't have Gradle installed, then first do [gradlew] or [gradlew.bat].  This will download what you need.
After that, simply use 'gradlew' in stead of 'gradle' to run any gradle-commands.


## run/develop:

1. Make sure you have Java runtime installed - version  1.8.x
2. Downloaded source for this project - either as a ZIP or with [hg clone https://andante-george@bitbucket.org/andante-george/george-client-jvm].
3. In a terminal/command-prompt, go to the root folder of the project.
4. If you don't have Gradle installed already, do [gradlew] (Mac/*nix) or [gradlew.bat](Windows).
5. To run it, do [gradle run]
6. To start a REPL at the terminal (for interactive "play" and developement), first do [gradle cp], then do [bin/repl] or [bin/repl.bat]



The following are the most relevant Gradle commands for this project:
```sh
gradle clean    # cleans/removes the build-dir

gradle build    # compiles and produces a basic jar

gradle run      # (builds) and runs the main app (with args hardcoded in build.gradle)
gradle example  # (builds) and runs a trivial example app (with args hardcoded in build.gradle)

gradle cp       # writes the necessary classpath to a temp-file.

bin/rep
bin\\repl.bat    # starts an interactive repl using the classpath form the command above
```

