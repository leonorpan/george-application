# George Client - jvm source

This is the main application code that goes into George Client - native (Windows, Mac, Linux?)

If you simply want to run George, it will be much quicker and easier for you to download and install the latest native version for your system. <br>
Go here to [get George for Windows & Mac](http://www.george.andante.no).


## Download

You will soon be able to download the source as a ZIP-file.  <br>
(I just need to implement some support for this. Remind me, if you see this message.)

However, if you would prefer to easily update the to the latest version, then use Mercurial and clone i stead, and/or use Atlassian's very useful tool 
SourceTree. (See the top of the [Overview](https://bitbucket.org/andante-george/george-client-jvm/overview) page for links and instructions.)


## Java

To run this code you will need to have Java 8v40 or newer installed on your system.<br>
(As of this writing I am on 8v74.)

If you wish to build it, you will need to have a JDK installed and your paths set up.

- [Download and install a *JDK*](http://www.oracle.com/technetwork/java/javase/downloads/)
- [Set up your path(s)](http://docs.oracle.com/javase/tutorial/essential/environment/paths.html)

## Boot

This project is set up to use [Boot](http://boot-clj.com) to build and run the code.

To get Boot on your system, simply run the "thin wrapper" by doing: `./boot/boot.sh` or `boot\boot.exe`. <br>
This will download and install the latest version of Boot for your system.

To update Boot, do `./boot/boot.sh -u` or `boot\boot.exe -u`.

For help with Boot itself, do: `./boot/boot.sh -h` or `boot\boot.exe -h`.

If you would rather use the short version of the boot-command, `boot`, you will have to add Boot to your path.<br>
If you understand what that means, then you will also be able to do that based on the instruction you find on the Boot home and download pages.


## run/develop:

In the listing bellow I will simply write `<boot>` in place of either `./boot/boot.sh` or `boot\boot.exe`.

The first time you run a <boot> command, Boot take a minute or two to update some repositories. <br>
The next time it will be a lot faster, though.


### The following commands are relevant for this project:

`<boot> repl` To start an interactive REPL.



## Maven?

Yes, there is a "pom.xml" in the source. But that is simply to facilitate sharing dependency information with my IDE (IntelliJ). <br>
Boot is able to read and write to this file, and the IDE can automatically update itself when it sees the changes.
