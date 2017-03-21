# George Client - jvm source



This is the main application code that goes into George Client - native (Windows, Mac, Linux?)  

If you simply want to run George, it will be much quicker and easier for you to download and install the latest native version for your system.   
Go here to [get George for Windows & Mac](http://www.george.andante.no).


## Documentation

To learn how to test and develop, contribute, and to see the API docs, see the [developer documentation](http://www.george.andante.no/docs).


## Download

You will soon be able to download the source as a ZIP-file.    
(I just need to implement some support for this. Remind me, if you see this message.)

However, if you would prefer to easily update to the latest version, then use Mercurial and clone instead, and/or use Atlassian's very useful tool
SourceTree. (See the top of the [Overview](https://bitbucket.org/andante-george/george-client-jvm/overview) page for links and instructions.)


## Java

To run this code you will need to have Java 8v40 or newer installed on your system.  
(As of this writing I am on 8v74.)

If you wish to build it, you will need to have a JDK installed and your paths set up.

- [Download and install a *JDK*](http://www.oracle.com/technetwork/java/javase/downloads/)
- [Set up your path(s)](http://docs.oracle.com/javase/tutorial/essential/environment/paths.html)

## Leiningen

This project is set up to use [Leiningen](http://leiningen.org) to build and run the code.

To get Boot on your system, simply run the "thin wrapper" by doing: `./lein.sh` or `lein.bat`.  
This will download and install the latest version of Boot for your system.

To update Leiningen, do `./lein.sh upgrade` or `lein.bat upgrade`.

For help with Leiningen itself, do: `./lein.sh -h` or `lein.bat -h`.

If you would rather use the short version of the lein-command, `lein`, you will probably have to add Leiningen to your path.  
If you understand what that means, then you will also be able to do that based on the instruction you find on the Leiningen home and download pages.


## run/develop

In the listing below I will simply write `<lein>` in place of either `./lein.sh` or `lein.bat`.

The first time you run a <lein> command, Leiningen will take a minute or two to update some repositories.  
The next time it will be a lot faster, though.


### The following commands are relevant for this project

`<lein> deps` To preload and/or update all dependencies.

`<lein> george.example` To run a simple george.example-script.

`<lein> examplej` To run a simple Java-class which in turn runs the same simple george.example-script.

`<lein> repl` To start an interactive REPL.
  
  
  

***

## License

Copyright © 2016 Terje Dahl

Distributed under the [Eclipse Public License 1.0](https://opensource.org/licenses/eclipse-1.0.php).

