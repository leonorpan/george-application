;; Copyright (c) 2016-2018 Terje Dahl. All rights reserved.
;; The use and distribution terms for this software are covered by the Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php) which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Terje Dahl"}
  george.audio.input
  (:require
      [clojure.pprint :refer [pprint pp] :as cpp]
      [clojure.data :as data]
      [clojure.core.async :refer [>!! <! chan timeout sliding-buffer thread go go-loop]]
      [george.javafx :as fx])


  (:import
      (javafx.scene.control ToggleGroup)
      (javax.sound.sampled AudioSystem TargetDataLine DataLine$Info AudioFormat$Encoding AudioFormat LineUnavailableException AudioInputStream)
      (javafx.scene Group)
      (java.nio ByteOrder ByteBuffer)
      (java.io ByteArrayOutputStream)))



(def DEFAULT_MIC_FORMAT
  (AudioFormat.
    AudioFormat$Encoding/PCM_SIGNED ; format
    (float 44100)  ; sample rate
    16        ; bits per sample (2 bytes)
    1          ; channels: mono!
    2          ; frame size in bytes - for PCM it is bytes pr sample x channels.
    (float 44100)  ; frame rate
    false))    ; is big endian


(def DEFAULT_MIC_TARGET_DATA_LINE_INFO
  (DataLine$Info. TargetDataLine DEFAULT_MIC_FORMAT))



(defn- contains-default [s] (or (.contains s "default") (.contains s "primary")))

(defn- no-default
  "The so-called Primary/Default mixer is conseptual, not useable and needs to be filtered out."
  [mixer-list]
  (filter #(-> % .getMixerInfo .getName .toLowerCase contains-default not) mixer-list))




(defn target-data-line? [line-info]
  (= (.getLineClass line-info) TargetDataLine))



(defn get-directed-lineinfo-from-mixer [mixer line-info]
  (if (target-data-line? line-info)
    (.getTargetLineInfo mixer line-info)
    (.getSourceLineInfo mixer line-info)))


(defn get-compatible-mixer-list [data-line-info]
  (->>
    ;; get mixer-info-list
    (AudioSystem/getMixerInfo)
    ;; extract mixers for each mixer-info
    (map
      (fn [mixer-info] (AudioSystem/getMixer mixer-info)))
    ;; only want mixers that have line-info for one or more compatible lines
    (filter
      (fn [mixer] (boolean (seq (get-directed-lineinfo-from-mixer mixer data-line-info)))))))



(defn print-mixer [mixer]
  (println mixer)
  (let [mixer-info (.getMixerInfo mixer)]
    (println "          mixer:" mixer)
    (println "           name:" (.getName mixer-info))
    (println "    description:" (.getDescription mixer-info))))




(defonce ^:private singletons (atom {}))

(defn- get-singleton [k]
    (-> @singletons k))

(defn- del-singleton [k]
    (swap! singletons dissoc k))

(defn- singleton
    "Gets singleton, or creates one using the provided function"
    [k create-f]
    (if-let [obj (get-singleton k)]
        obj
        (let [obj (create-f)] (swap! singletons assoc k obj) obj)))


"
A line, if opened and closed, can not be reopened.
Therefore: When new lines are aquired and opened,
hang on to their reference even after it becomes unavaiable.
That way, if the line again becomes available, we can then continue using it (without opening it again).

Lines can be started and stopped repeatedly.
It there a need / purpose for for this, though, as long as whatever data they are feedaign gets drained?
"



(def ^:private MID-line-atom
    "Contains all opened lines, keyed by MID: {MID1 TDL1, MID2 TDL2}"
    (atom {}))




(defonce ^:private consumers
         #_"A map of maps: {MID {Object channel}}.
         There is also key :selected-MID for consumers subscribing to whatever input is selected:

          The channels are all audio data consumers.  They must not block!
          (Should be sliding or dropping buffers).

          The format of the audio data is: ...
          "
         (atom {}))


(defn- add-consumer [MID obj ch]
    (println "add-consumer obj:" obj " ch:" ch)
    (swap! consumers assoc-in [MID obj] ch))



(defn- feed-consumers
    "puts given data on all channels listening to the given MID"
    [MID data]
    (loop [chans (@consumers MID)]
        (when-let [[_ ch] (first chans)]
            (>!! ch data)
            (recur (rest chans)))))


(defn- feeder-loop
    "Reads data from the line matching the MID, and feeds it to consumers via 'feed-consumer'"
    [MID]
    (println "feeder-loop MID:" MID)
    (let [
          line (@MID-line-atom MID)
          AIS (AudioInputStream. line)
          buffer-size 4096 ;; 44.1k Hz / 2k samples = 22 Hz
          buffer (byte-array buffer-size)
          BAOS (ByteArrayOutputStream. buffer-size)]

        (.start line)
        (go-loop [len (.read AIS buffer)]
            (.write BAOS buffer 0 len)
            (feed-consumers MID (.toByteArray BAOS))
            (.reset BAOS)
            (recur (.read AIS buffer)))))


#_(defn audio-bytes->samples [bytes bigendian?]
    (let [
          tempBB (java.nio.ByteBuffer/wrap bytes)
          short-count (/ (count bytes) 2)]

        (when-not bigendian? (.order tempBB java.nio.ByteOrder/LITTLE_ENDIAN))
        (short-array short-count
            (for [_ (range short-count)]
                (.getShort tempBB)))))


(defn audio-bytes->samples [bytes bigendian?]
    (let [samples-array (short-array (/ (count bytes) 2))]
        (-> (ByteBuffer/wrap bytes)
            (.order (if bigendian? ByteOrder/BIG_ENDIAN ByteOrder/LITTLE_ENDIAN))
            .asShortBuffer
            (.get samples-array))
        samples-array))


(def ^:private all-MIDs-atom
  "Contains all mixers ever found, whether available now or not.
  The line will have been opened (but probably not started, or stopped if previously started.
  Each entry is a map with keys :MID :TDL"
  (atom #{}))

(def ^:private current-MIDs-atom
  "Contains a reference to all MIDs/TDLs currently available in system"
  (atom #{}))

(def ^:private selected-MID-atom
  #_"contains a reference to selected MID/TDL."
  (atom nil))


(defonce ^:private selected-input-panes-atom
         #_"a set of panes which will contain"
         (atom #{}))


(declare set-active)
(declare create-monitor-pane)

(defn- selected-MID-watcher [k r o n]
    (when (not= o n)
       ; (println "selected-MID-watcher MID changed:" n)
        (doseq [pane @selected-input-panes-atom]
            (fx/set! pane
                     (doto (create-monitor-pane n)
                         (set-active))))))



(defn mixer->MID
    "Sometimes the MixerInfo may have its name truncated.
     To avoid treating known mixers as new because the name was truncated in one instance and not another,
     we in stead always compare mixers by MID, not MixerInfo."
    [mixer]
    (let [m (-> mixer .getMixerInfo)]
        {:name (apply str (take 30 (.getName m)))
         :description (.getDescription m)
         :vendor (.getVendor m)
         :version (.getVersion m)}))




(defonce MIM-mixer-atom (atom {}))


(defn- get-TDL-from-mixer [mixer]
    (let [lines (.getTargetLines mixer)]
        (if-not (empty? lines)
            (first lines)
            (.getLine mixer DEFAULT_MIC_TARGET_DATA_LINE_INFO))))


(defn open-TDL [TDL]
    (println "open-TDL")
    (.open ^TargetDataLine TDL DEFAULT_MIC_FORMAT)
    TDL)


(defn open-TDL-safe [TDL]
    (try
        (open-TDL TDL)

        (catch LineUnavailableException lue
            (println " # LineUnavailableException (in open-TDL-safe):" lue)
            (.printStackTrace lue)
            nil)

        (catch Exception e
            (println " # Exception (in open-TDL-safe):" e)
            (.printStackTrace e)
            nil)))



(defn refresh-MIDs
    "queries for compatible lines, updates current-MIDs-atom, updates all-MIDs-atom
    Returns vector of changed current lines: [added-lines removed-lines]"
    []
    (let [
          found-mixers
          (no-default (get-compatible-mixer-list DEFAULT_MIC_TARGET_DATA_LINE_INFO))

          found-MIM-mixers
          (into {} (map (fn [m] [(mixer->MID m) m]) found-mixers))

          found-MIDs
          (keys found-MIM-mixers)

          [added-MIDs-set removed-MIDs-set _]
          (data/diff (set found-MIDs) @current-MIDs-atom)]

        (println "    added-MIDs-set:" added-MIDs-set)
        (println "  removed-MIDs-set:" removed-MIDs-set)

        (loop [MIDs added-MIDs-set]
            (when-let[MID (first MIDs)] ;; sentinel
                ;(println "new MID:" MID)
                (when-not (@all-MIDs-atom MID)
                    ;(println "Unseen! Adding to all-MIDs-atom")
                    (swap! all-MIDs-atom conj MID)

                    ;(println "    TODO: get line, open, add to lookup-map")
                    (let [
                          mixer (found-MIM-mixers MID)
                          _ (println "  ## mixer:" mixer)
                          TDL (open-TDL-safe (get-TDL-from-mixer mixer))]

                        ;; perhaps don't need to store mixer for mim?  but what the heck ...
                        (swap! MIM-mixer-atom assoc MID mixer)
                        ;; store this opened line "permanently"
                        (swap! MID-line-atom assoc MID TDL)
                        ;; start a read-loop which feeds to all consumers for that TDL
                        (feeder-loop MID)))

                ;(println "Adding to current-MIDs-atom")
                (swap! current-MIDs-atom conj MID)
                (recur (rest MIDs))))

        (when (seq removed-MIDs-set)
            (swap! current-MIDs-atom
                   (fn [clines]
                       (set (filter #(not (removed-MIDs-set %)) clines)))))

        ;; make sure there is a "watch" on selected-MID-atom
        (when (zero? (count (.getWatches selected-MID-atom)))
            (add-watch selected-MID-atom :selected-MID-watcher selected-MID-watcher))

        ;; Update selected-MID-atom if necessary. If no MIDs, then nil
        (when-not (@current-MIDs-atom @selected-MID-atom)
            (reset! selected-MID-atom (first @current-MIDs-atom)))

        [added-MIDs-set removed-MIDs-set]))



(defn get-selected-MID
  "Returns the selected MID/TDL if not nil, and if in current-MIDs-atom,
  else it will return the first (default) MID/TDL from current-MIDs-atom"
  []
  (when-not @selected-MID-atom
      (refresh-MIDs))
  @selected-MID-atom)






(defn- ignite [lights prosent sticky-prosent]
  "'turn on' number of lights relative to prosent level and also lights the light at sticky-prosent"
  (let [
        len (count lights)
        lim-activate (Math/round (* (/ len 100.) prosent))
        sticky-activate (Math/round (* (/ len 100.) sticky-prosent))
        rev-lights (vec (reverse lights))]

    (fx/later
      (doseq [l rev-lights]
        (.setFill l fx/GREY))
      (doseq [l (take lim-activate rev-lights)]
        (.setFill l fx/BLUE))
      (if(< 0 sticky-activate len)
        (.setFill (get rev-lights sticky-activate) fx/BLUE)))))




(defn- level-meter [label]
  (let [
        lights
        (vec (for [_ (range 10)]
               (fx/rectangle :size [20 3] :fill fx/BLUE :arc 3)))
        lights-pane
        (apply fx/vbox (conj lights :spacing 1 :padding 2))

        outer-pane
        (doto (fx/vbox (fx/text label) lights-pane)
          (fx/set-padding 20 20 0 20))]

    (ignite lights 10 10)

    [outer-pane lights]))


(defn- set-active [monitor-pane]
    (let [
          {:keys [MID lights-pane]} (.getUserData monitor-pane)
          active (boolean (@current-MIDs-atom MID))]

        (.setVisible lights-pane active)))



(defn- clamp [low val high]
    (let [
          val (if (< val low) low val)
          val (if (> val high) high val)]

        val))


(defn- biggest-sample [samples]
    (max
        (apply max samples)
        (- (apply min samples))))


(defn- calculate-prosent [data]
    (-> data
        (audio-bytes->samples (.isBigEndian DEFAULT_MIC_FORMAT))
        biggest-sample
        (/ Short/MAX_VALUE)
        (* 100.)))


(defn- create-monitor-channel [MID meter-lights]
    (let [c (chan (sliding-buffer 1))]
        (go-loop [data (<! c)
                  cnt 0 prev-prosent 0 prev-sticky-prosent 0 prev-sticky-countdown 0]
            (let [
                  update?
                  (= cnt 0)

                  prosent
                  (if update?
                      (calculate-prosent data)
                      prev-prosent)

                  [sticky-prosent sticky-countdown]
                  (if (> prosent prev-sticky-prosent)
                      [prosent 30]
                      (if (= prev-sticky-countdown 0)
                          [0 0]
                          [prev-sticky-prosent (dec prev-sticky-countdown)]))

                  sticky-prosent
                  (clamp 0 sticky-prosent 100)]

                (when update?
                    (ignite meter-lights prosent sticky-prosent))

                (recur (<! c)
                       (mod (inc cnt) 2) prosent sticky-prosent sticky-countdown)))
        c))


(defn- create-monitor-pane [MID]
    (let [
          name (:name MID)
          description (:description MID)

          [meter-pane meter-lights]
          (level-meter name)

          lights-pane
          (apply fx/vbox (conj meter-lights :spacing 1 :padding 2))

          texts
          (fx/vbox (fx/text name) (fx/text description))

          outer-pane
          (doto
              (fx/hbox texts lights-pane)
              (.setUserData {:MID MID :lights-pane lights-pane}))]

        (add-consumer MID outer-pane (create-monitor-channel MID meter-lights))

        outer-pane))


(defn- create-radiobutton-monitor-pane [MID togglegroup]
    (let [
          monitor-pane (create-monitor-pane MID)

          radio-button
          (doto (fx/radiobutton)
              (.setToggleGroup togglegroup)
              (.setGraphic monitor-pane))]


        (doto (fx/hbox radio-button monitor-pane)
            (.setAlignment fx/Pos_CENTER)
            (fx/set-padding 0 0 0 20)
            (.setUserData {:MID MID
                           :lights-pane (-> monitor-pane .getUserData :lights-pane)
                           :radiobutton radio-button}))))






(defn refresh-inputs-pane [monitors-vbox]
    (println "refresh-inputs-pane ...")

    (refresh-MIDs)

    (let [
          monitors
          (-> monitors-vbox .getChildren)

          monitor-MIDs-set
          (set (map #(-> % .getUserData :MID) monitors))]

        ;; add monitors for MIDs
        (loop [MIDs @all-MIDs-atom]
            (when-let[m (first MIDs)]
                ;(pprint MID)
                (when-not (monitor-MIDs-set m)
                    (println "adding monitor for MID:" m)
                    (fx/add monitors-vbox
                            (create-radiobutton-monitor-pane
                                m
                                (-> monitors-vbox .getUserData :togglegroup))))

                (recur (rest MIDs))))

        ;; activate/deactivate monitors based on current-MIDs
        (loop [monitors (-> monitors-vbox .getChildren)]
            (when-let [m (first monitors)]
                (let [
                      {:keys [MID radiobutton]} (.getUserData m)
                      selected (= @selected-MID-atom MID)]

                    (set-active m)
                    (.setSelected radiobutton selected))

                (recur (rest monitors))))))






(defn- input-selector-pane []
  (let [
        togglegroup
        (doto (ToggleGroup.)
            (-> .selectedToggleProperty
                (.addListener
                    (fx/changelistener
                        [_ _ _ monitor-radiobutton]
                        (let [MID (-> monitor-radiobutton .getParent .getUserData :MID)]
                            ;(println "selected MID:" MID)
                            (when-not(= @selected-MID-atom MID)
                                (reset! selected-MID-atom MID)))))))

        monitors-vbox
        (doto (fx/vbox)
            (.setUserData {:togglegroup togglegroup}))

        refresh-button-action
        #(do (refresh-inputs-pane monitors-vbox)
             (-> monitors-vbox .getScene .getWindow .sizeToScene))

        refresh-button
        (fx/button "Refresh" :onaction refresh-button-action)

        top
        (fx/hbox refresh-button)

        pane
        (fx/borderpane :top top :center monitors-vbox)]


      [pane refresh-button-action]))





(defn selected-input-pane []
    (let [pane (fx/group)]
        (refresh-MIDs)
        (fx/set! pane (create-monitor-pane (get-selected-MID)))
        ;; FIX: Memory leak: The panes aren't removed again when disposed of
        (swap! selected-input-panes-atom conj pane)
        pane))


(defn- create-selected-input-stage []
    (fx/now
        (let []
            (fx/stage
                :title "selected input monitor"
                :scene (fx/scene (selected-input-pane))
                :sizetoscene true))))




(defn- create-input-selector-stage []
  (fx/now
      (let [
            [pane refresh-button-action]
            (input-selector-pane)

            stage
            (fx/stage
                :title "audio input selector"
                :scene (fx/scene pane)
                :sizetoscene true
                :onhidden #(del-singleton :input-selector-stage))]


          (refresh-button-action)
          (.setUserData stage {:refresh-button-action refresh-button-action})
          stage)))


(defn input-selector-stage []
  (singleton :input-selector-stage create-input-selector-stage))


;;
;(do (println "WARNING! Running george.audio.input/inputs-stage")  (input-selector-stage) (create-selected-input-stage))