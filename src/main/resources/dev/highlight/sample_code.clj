;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(+ 1.1 2 (+ 3 4) 5
  ( - 8 7) {:aaa "AAA" ::bb "BB"})
[\a \b \c \newline]
#{ "T" "D"}
1.1 1/2  45N
 2r101010 8r52 36r16 42


(def a "A")
(println a)

true false nil
/
NaN  -Infinity Infinity +Infinity

;; something more complicated
@an-atom
#(println "name:" % %2)
\uE001
\o17
#"regex" {()}
'a-symbol ([])
~arg1
'~arg2
`syn-qt
~@assert
"A\tdifficult\nstring\\
over lines!"

genarated-sym#
 ()
 ( %)  ( %2) %13 %& %x
 []
(
     ( () ())
     []
     { () []})

