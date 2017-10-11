# Change Log

All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).



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
