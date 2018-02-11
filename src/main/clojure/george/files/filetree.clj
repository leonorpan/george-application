;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns george.files.filetree
  (:require
    [clojure.pprint :refer [pprint]]
    [environ.core :refer [env]]
    [george.javafx :as fx]
    [george.javafx.java :as fxj]
    [clojure.string :as cs])
  (:import
    [javafx.scene.control TreeView TreeItem TreeCell ScrollBar]
    [java.nio.file FileSystems FileSystem Files Paths Path FileVisitOption LinkOption StandardCopyOption]
    (java.net URI)
    (javafx.scene.paint Color)
    (javafx.util Callback)
    (javafx.scene.input TransferMode ClipboardContent)
    (javafx.scene Cursor SnapshotParameters)
    (java.io IOException)
    (javafx.collections ObservableList)
    (javafx.geometry Orientation)))


(defn ^String filename [path]
  (str (.getFileName path)))


(defrecord StringablePath [path]
  Object
  (toString [_]
    (filename path)))


(defn to-path [s & args]
  (Paths/get s (into-array String args)))

(defn to-string [path]
  (-> path .toAbsolutePath str))


;; TODO: better icons?
(defn file-image [] 
  (fx/imageview "graphics/file-16.png"))
(defn  folder-image [] 
  (fx/imageview "graphics/folder-16.png"))



(defn is-dir [path]
  (Files/isDirectory path (make-array LinkOption 0)))

(defn- treecell->path [treecell]
  (-> treecell .getTreeItem .getValue))

(declare lazy-filetreeitem)


(defn- move-file [source-path target-path]
  (try
    (Files/move source-path
                target-path
                (fxj/vargs StandardCopyOption/REPLACE_EXISTING StandardCopyOption/ATOMIC_MOVE))
    (catch IOException e (.printStackTrace e))))


(defn- get-that-path [event]
  (to-path (.getString (.getDragboard event))))


(defn- will-receive? [this-path that-path]
  (let [dir? (is-dir this-path)
        that-parent (.getParent that-path)
        same-parent? (Files/isSameFile this-path that-parent)
        same? (Files/isSameFile this-path that-path)]
    ;(println "  ##    this-path:" (to-string this-path))
    ;(println "  ##         dir?:" dir?)
    ;(println "  ##    that-path:" (to-string that-path))
    ;(println "  ##  that-parent:" (to-string that-parent))
    ;(println "  ## same-parent?:" same-parent?)
    ;(println "  ##        same?:" same?)
    (and dir? (not same-parent?) (not same?))))


(defn mark-droppable [treecell receiving?]
  (let [w [1 0 1 0]]
    (doto treecell
      (.setBorder 
        (fx/new-border (if receiving? Color/BLUE Color/TRANSPARENT) w)))))


(defn make-droppable [treecell treeitem]
  ;; TODO: Ensure that children are also "ghosted" when dragging
  (mark-droppable treecell false)

  (.setOnDragOver treecell
    (fx/event-handler-2 [_ event] ;; DragEvent
      (let [this-path (.getValue treeitem)
            that-path (get-that-path event)]
        (when (will-receive? this-path that-path)
          (.acceptTransferModes event (fxj/vargs TransferMode/MOVE))))))
          ;(.consume event)))))
  
  (.setOnDragEntered treecell
    (fx/event-handler-2 [_ event] ;; DragEvent
      ;(println " drag-entered:" (to-string (.getValue treeitem)))
      (when (will-receive? (.getValue treeitem) (get-that-path event))
            ;(println "marking dir" (.getValue treeitem))
            (mark-droppable treecell true))
      (.consume event)))

  (.setOnDragExited treecell
    (fx/event-handler-2 [_ event] ;; DragEvent
       ;(println "un-marking dir" (.getValue treeitem))
       (mark-droppable treecell false)
       (.consume event)))

  ;; TODO: make receiving folder selected in view
  (.setOnDragDropped treecell
    (fx/event-handler-2 [_ event]
       (let [this-path (.getValue treeitem)
             that-path (get-that-path event)]
         (when (will-receive? this-path that-path)
           (move-file that-path (to-path (to-string this-path) (filename that-path)))
           (.refresh treeitem)
           (.setDropCompleted event true)
           (-> treecell .getTreeView .getSelectionModel 
               (.selectIndices (.getIndex treecell) (int-array 0))))
         (.consume event))))
  
  treecell)


(defn make-draggable [treecell]
  (let [press-XY (atom nil)
        treeitem (.getTreeItem treecell)
        path (.getValue treeitem)]
    (doto treecell
      (.setOnMousePressed
        (fx/event-handler-2 [_ me] (reset! press-XY (fx/XY me))))

      ;(.setOnMouseDragged
      ;  (fx/event-handler-2 [_ me] (.consume me)))

      (.setOnDragDetected
        (fx/event-handler-2 [_ me]
           ;(println "starting drag: " treecell)
           (let [db
                 (.startDragAndDrop treecell (fxj/vargs TransferMode/MOVE))
                 cc
                 (doto (ClipboardContent.) (.putString (to-string path)))

                 [x y] @press-XY
                 [w h] (fx/WH treecell)
                 hoff (- (/ w 2) x)
                 voff (- y (/ h 2))
                 ;_ (println "[x y]:" [x y])
                 ;_ (println "[w h]:" [w h])
                 ;_ (println "hoff:" hoff)
                 ;_ (println "voff:" voff)

                 ghost
                 (doto (SnapshotParameters.)
                       (.setFill Color/TRANSPARENT))]
             
             (.setCursor treecell Cursor/MOVE)
             (.setOpacity treecell 0.8)
             (.setDragView db (.snapshot treecell ghost nil) hoff voff)
             ;(.setOpacity treecell 0.2)
             (.setOpacity treecell 1.0)

             (.setContent db cc)
             (.consume me))))

      (.setOnDragDone
        (fx/event-handler-2 
          [_ me]
          (.setOpacity treecell 1.0)
          (.setCursor treecell Cursor/DEFAULT)
          (when (.getTransferMode me)
            (.refresh (.getParent treeitem)))
          (.consume me))))))


(defn- make-doubleclickable [treecell]
  (.setOnMouseClicked 
    treecell
    (fx/event-handler-2 
      [_ e]
      (let [path (treecell->path treecell)]
        (when (and  (not (is-dir path)) 
                    (= (.getClickCount e) 2))
              (println "FILE double-click:" (filename path)))))))


(defn path-treecell
  "Returns a custom TreeCell.
  '->str' is optional 1-arg function which takes at item and returns a String."
  []
  (proxy [TreeCell] []
    (updateItem [^Path path empty?]
      (proxy-super updateItem path empty?)
      (if (or (nil? path) empty?)
        (doto this
          (.setGraphic nil)
          (.setText nil)
          (fx/set-tooltip nil))
        ;; else
        (let [dir? (is-dir path)]
          (doto this
            (.setGraphic (if dir? (folder-image) (file-image)))
            (.setText (filename path))
            (fx/set-tooltip (to-string path))
            (make-doubleclickable)
            (make-draggable)
            (make-droppable (.getTreeItem this))))))))


(defn path-treecell-factory
  "Returns a custom Callback."
  []
  (reify Callback
    (call [_ _]
      (path-treecell))))


(definterface Refreshable
  (refresh []))


(defn- filename-lowercased [p]
  (cs/lower-case (filename p)))


(defn alphabetized [paths]
  (sort-by filename-lowercased paths))


(defn not-hidden [paths]
  (filter #(not (Files/isHidden %)) paths))


(defn- get-paths [parent-path]
  (->  parent-path
       Files/newDirectoryStream
       vec
       not-hidden
       alphabetized))


(defn- update-children [treeitem path]
  (let [children ^ObservableList (.getChildren treeitem)
        paths (get-paths path)
        c-cnt (count children)       
        p-cnt (count paths)]
    (when (not= c-cnt p-cnt)
      ;; if children-cnt > paths-count, figure out which child to remove, else figure out which path to add
      (let [remove? (> c-cnt p-cnt)]
        (loop [ix 0 paths paths]
          (if remove?
            (when (< ix (count children))
              (let [shown-p (.getValue ^TreeItem (.get children ix))]
                  (if (Files/exists shown-p (into-array [LinkOption/NOFOLLOW_LINKS]))
                      (recur (inc ix) (rest paths))
                      (do
                        (.remove children ix)
                        (recur ix (rest paths))))))

            (let [shown-p (when (< ix (count children)) (.getValue ^TreeItem (.get children ix)))
                  real-p (first paths)]
              (if (and shown-p real-p (Files/isSameFile shown-p real-p))
                  (recur (inc ix) (rest paths))
                  (when (and real-p (or (nil? shown-p) (not (Files/isSameFile shown-p real-p)))) 
                    ;(println "adding path at" ix (to-string real-p))
                    (.add children ix (lazy-filetreeitem real-p))
                    (recur (inc ix) (rest paths)))))))))))


(defn- set-children [treeitem path]
  (let [children (.getChildren treeitem)
        paths (get-paths path)]
    (.setAll  children (fxj/vargs* (map #(lazy-filetreeitem %) paths)))))
                             


(defn lazy-filetreeitem [path]
  (let [dir?  (is-dir path)
        first-children-call_  (atom true)     
        item
        (proxy [TreeItem Refreshable] [path (if dir? (folder-image) (file-image))]
          (isLeaf [] (not dir?))
          (refresh []
              (when (not @first-children-call_)
                (update-children this path)))
          (getChildren [] 
            (when @first-children-call_
              (reset! first-children-call_ false)
              ;(println "first call:" (filename path))
              (set-children this path))
            (proxy-super getChildren)))]
    item))

;; TODO: implement tooltip for files (path mod-date, etc.)
;; TODO: implement context-menu on items (open/open in tab (for folders), edit, delete.
;; TODO: detect action - i.e. double-click or CTRL-O or CTRL-ENTER
;; TODO: Maybe detect and handle file edit (ENTER or long click?)
;; TODO: detect and handle filesystem changes ...


(defn tree-root [^Path path]
  (doto (lazy-filetreeitem path)
        (.setExpanded true)))


(defn- tree-listener []
  (fx/changelistener [_ _ _ item]
     (when item
       (let [path (-> item .getValue)]
         (if (is-dir path)
           (println "DIR:" (filename path))
           (println "FILE:" (filename path)))))))
       

(defn- get-scrollbar [treeview]
  (let [nodes (.lookupAll treeview ".scroll-bar")]
    (first (filter #(and (instance? ScrollBar %)
                         (= (.getOrientation %) Orientation/VERTICAL))
                    nodes))))


(defn- make-autoscrolling [treeview]
  (.setOnDragOver treeview
    (fx/event-handler-2 [_ me]
      (let [y (.getY me)
            bounds (.getBoundsInLocal treeview)
            h (.getHeight bounds)
            want-to-scroll? (not (< 50 y (- h 50)))
            up? (< y 50)]
        (when want-to-scroll?
          (when-let [scrollbar (get-scrollbar treeview)]
            (.setUnitIncrement scrollbar 1)
            (if up? (.decrement scrollbar)
                    (.increment scrollbar))))))))


(defn- file-tree [^Path path]
    (doto (TreeView. (tree-root path))
          (.setCellFactory (path-treecell-factory))
          (make-autoscrolling)
          (-> .getSelectionModel 
              .selectedItemProperty 
              (.addListener (tree-listener)))))


(defn- set-filetree [borderpane path]
  (.setCenter borderpane (file-tree path)))


(defn- panel-folder [label path borderpane]
  (fx/new-label
    label
    :graphic (folder-image)
    :mouseclicked #(set-filetree borderpane path)))


(defn- file-nav []
  (let [
        root
        (fx/borderpane)

        George-path
        (to-path (env :user-home) "Documents" "George")
   
        panel
        (fx/vbox
          (panel-folder "George" George-path root)
          (panel-folder "Desktop" (to-path (env :user-home) "Desktop") root)
          (panel-folder "Documents" (to-path (env :user-home) "Documents") root)
          (panel-folder "Home" (to-path (env :user-home) ) root)
          :spacing 5          
          :insets [5 20 5 20])]    
    
    (doto root
      (.setLeft panel)
      (set-filetree George-path))))
     

(defn stage [root]
  (let []
    (fx/later
      (fx/stage
        :title "File navigator"
        :scene (fx/scene root)
        :tofront true
        :size [500 500]
        :sizetoscene false))))

;;; 

; (when (env :repl?) (stage (file-nav))) 


