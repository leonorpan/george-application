# Change Log

All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).


## [2018.4.1] - 2018-02-03

### Turtle API
- Prevented a deadlock-issue between move-to and ticker. 
- Renamed assoc-/dissoc-onkey to set-/unset-onkey.
- Added namespaces "george.turtle.tom" and "george.turtle.adhoc.jf4k".


## [2018.4] - 2018-01-31

### Turtle API
- Added support for animation, and for keyboard-input.
- Implemented a long list of new commands:
is-overlap, get-overlappers, set-/get-/reset-/start-/stop-ticker/is-ticker-started, assoc-/dissoc-/get-/get-all/reset-onkey, to-front
- And a new demo: samples/asteroids

## [2018.3] - 2018-01-25

### Added
- Better handling of prep-ing of default turtle-namespace, using macros.
- 2 special macros: `g/turtle-ns` and `g/create-turtle-ns` which behave pretty much like their counterparts in clojure.core. 

### Changed
- Reorganized some namespaces.

### Turtle API
- `screen` is now thread-safe.
- Improvements to `filled` and `filled-with-turtle`
- Implemented "fencing" of screen - with :wrap/:stop/:none/functions
- Implemented 'move', 'move-to', 'turn', 'turn-to', 'distance-to', 'heading-to'
- Implemented 'arc-left', 'arc-right'
- Added a color palette to the help-window


## [2018.2] - 2018-01-16

### Added
- 'Load' command in editor, similar to 'Run' but less chatty.

### Changed
- Output now has left margin which shows the chars and colors that used to be printed, making copying from output easier, and the output tidier. 
- Rewrote a large part of the turtle API, and extended it considerably.

### Turtle API
- Pen shape control: 'set-round', 'is-round'
- Working with multiple turtles: 'new-turtle', 'clone-turtle', 'with-turtle'
- Filled figures: 'filled', 'filled-with-turtle', 'set-fill', 'get-fill'
- Writing on-screen: 'write', 'set-font', 'get-font'
- Running multiple turtles concurrently (in threads): Use 'future'


## [2018.1] - 2018-01-11

### Fixed
- Regression: CTRL-C now is "copy" in editor again, not "close tab"
- \*err\* messages from nrepl now also get printed.
- A nagging JavaFX Toolkit load/repl issue.

### Added
- Extensive master/detail "Turtle API" window, pulling content from docs and other texts in turtle API.
- Markdown parsing and HTML rendering of/for Turtle API. 
- New turtle commands: 'set-width'/'get-width', 'set-visible', 'set-pen-down'.
- Enhanced color handling in Turtle API.
- Library "defprecated" now prints warning when deprecated "turtle commands" are used.

### Changed
- Altered name of certain turtle "getter" commands.  
- Moved previous minimal embedded command list into a separate "Turtle API" tool window with link.
- Select color in editor now becomes gray when editor loose focus.
- Clojure 1.8 -> 1.9
- Sensible defaults: 1 editor and 1 input open, and input's "clear" not checked.
- Moved namespace 'george.application.turtle.turtle' to 'george.turtle'

### Removed
- Unused modules from code - including Paredit and cider-nrepl.


## [2018.0] - 2018-01-04

This is a major upgrade, with many changes.  
A few highlight:

- Single window application
- New custom text-editor with Parinfer and "blocks"
- Editor in tabs with robust file handling
- Enhanced REPL usage, error handling, nREPL server control
- Improved L&F


## [0.8.2] - 2017-10-11

### Changed
- Removed keyboard shortcuts from "history" buttons, as they were often accidentally triggered while navigating in code-editor.

### Fixed
- Using undo/redo is now more stable and safe. It should no longer cause rendering artifacts or multiple (or no) cursors.


## [0.8.1] - 2017-05-17

### Changed
- Adjusted coloring of code.

### Fixed
- Turtle screen does not persistently take focus during code execution.
- Paredit now works better - parens stay matched(!), and "slurp", "barf", "raise" work.  Also, better handling of marking and cursor location.
- Starting a Run/Eval via keyboard shortcut for is now also disabled during an ongoing run.

### Added
- Ability to copy or save Turtle screen snapshot from contextual menu.
- Resizing code (text) via CTRL/CMD-+/- - from 6 to 72 px.
- 'set-speed' in Turtle API - 10 is default 15 is as fast as it will animate, 'nil' skips all animation.
- A drop-down menu (in Input) disables/enables Paredit.


## [0.8.0] - 2017-05-03

### Changed
- George now uses nREPL for all evaluation - instead of custom REPL.

### Added
- True REPL/Eval interrupt from Input-window.
- Error-dialog informing user of error if Output not open.
- Stacktrace in Output and Error-dialog - uses clj-stacktrace.
- Attempts to parse location of error - displayed in Output and Error-dialog.


## [0.7.4] - 2017-04-19

### Changed
- Input window gets focus after execution/Eval.

### Fixed
- Long-running Eval no longer prevents George from exiting


## [0.7.3] - 2017-03-27

### Changed
- Input stages now "stagger" their layout nicely.
- Other adjustments to layout - to accomodate very small computer screens.
- Eval-button is disabled during execution - to prevent users from running code multiple times in (conflicting) threads.

### Added
- About label/button on launcher.
- A new Turtle command 'rep' - a simpler version of Clojures 'dotimes'.
- A small window listing basic available Turtle commands.


## [0.7.2] - 2017-03-18 [UNDEPLOYED]

### Removed
- The "IDE" application on the launcher. It caused confusion and errors.


## [0.7.1] - 2017-02-21

### Changed
- Most windows change from "tool pallets" to standard windows.

### Fixed
- A divide-by-zero exception for certain Turtle rotations. Caused an ugly printout.


## [0.7.0] - 2017-02-27

Base version open for contributions.


## [0.6.3] - 2016-11-23

Ready for course "Clojure 101"


## [0.6.2] - 2016-11-07


## [0.6.1] - 2016-10-05

First version used in a school.


<!--
[Unreleased]: https://github.com/your-name/{{name}}/compare/0.1.1...HEAD
[0.1.1]: https://github.com/your-name/{{name}}/compare/0.1.0...0.1.1
-->
