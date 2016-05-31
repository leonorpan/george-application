(ns
  ^{:author "Terje Dahl"}
  george.audio.input
  (:require
      [clojure.pprint :refer [pprint pp] :as cpp]
      [clojure.data :as data]
      [george.javafx.core :as fx]
    :reload
    )

  (:import (javafx.scene.control ToggleGroup)
           (javax.sound.sampled AudioSystem TargetDataLine DataLine$Info AudioFormat$Encoding AudioFormat)))

(def SELECTED_BACKGROUND_COLOR (fx/web-color "0x3875d7"))



(def DEFAULT_MIC_FORMAT
  (AudioFormat.
    AudioFormat$Encoding/PCM_SIGNED ; format
    (float 44100)	; sample rate
    16  			; bits per sample (2 bytes)
    1	    		; channels: mono!
    2		    	; frame size in bytes - for PCM it is bytes pr sample x channels.
    (float 44100)	; frame rate
    false ) )		; is big endian


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


(def ^:private MID-line-map-atom
    "Contains all opened lines, keyed by MID: {MID1 TDL1, MID2 TDL2}"
    (atom {}))


"
A line, if opened and closed, can not be reopened.
Therefore: When new lines are aquired and opened,
hang on to their reference even after it becomes unavaiable.
That way, if the line again becomes available, we can then continue using it (without opening it again).

Lines can be started and stopped repeatedly.
It there a need / purpose for for this, though, as long as whatever data they are feedaign gets drained?
"

(def ^:private all-MIDs-atom
  "Contains all mixers ever found, whether available now or not.
  The line will have been opened (but probably not started, or stopped if previously started.
  Each entry is a map with keys :MID :TDL"
  (atom #{}))

(def ^:private current-MIDs-atom
  "Contains a reference to all MIDs/TDLs currently available in system"
  (atom #{}))

(def ^:private selected-MID-atom
  "contains a reference to selected MID/TDL."
  (atom nil))


(defn- get-selected-MID
  "Returns the selected MID/TDL if not nil, and if in current-MIDs-atom,
  else it will return the first (default) MID/TDL from current-MIDs-atom"
  []

  )

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




(defn- ignite [lights prosent sticky-prosent]
  "'turn on' number of lights relative to prosent level and also lights the light at sticky-prosent"
  (let [
        len (count lights)
        lim-activate (Math/round (* (/ len 100.) prosent))
        sticky-activate (Math/round (* (/ len 100.) sticky-prosent))
        rev-lights (vec (reverse lights))
        ]
    (fx/later
      (doseq [l rev-lights]
        (.setFill l fx/GREY))
      (doseq [l (take lim-activate rev-lights)]
        (.setFill l fx/BLUE))
      (if(< 0 sticky-activate len)
        (.setFill (get rev-lights sticky-activate) fx/BLUE))
      )))



(defn- level-meter [label]
  (let [
        lights
        (vec (for [_ (range 10)]
               (fx/rectangle :size [20 3] :fill fx/BLUE :arc 3)))
        lights-pane
        (apply fx/vbox (conj lights :spacing 1 :padding 2))

        outer-pane
        (doto (fx/vbox (fx/text label) lights-pane)
          (fx/set-padding 20 20 0 20))
        ]
    (ignite lights 10 10)

    [outer-pane lights] ))


(defn- monitor-pane [MID togglegroup]
    (let [
          name (:name MID)
          description (:description MID)

          [meter-pane meter-lights]
          (level-meter name)

          lights-pane
          (apply fx/vbox (conj meter-lights :spacing 1 :padding 2))

          texts (fx/vbox (fx/text name) (fx/text description))
          texts-and-meter (fx/hbox texts lights-pane)

          radio-button
          (doto (fx/radiobutton)
              (.setToggleGroup togglegroup)
              (.setGraphic texts-and-meter)
              )
          ]
        (doto (fx/hbox radio-button texts-and-meter)
            (.setAlignment fx/Pos_CENTER)
            (fx/set-padding 0 0 0 20)
            (.setUserData {:MID MID :lights-pane lights-pane :radiobutton radio-button})
            )))


(defn refresh-MIDs
    "queries for compatible lines, updates current-MIDs-atom, updates all-MIDs-atom
    Returns vector of changed current lines: [added-lines removed-lines]"
    []
    (let [
;          all-MIDs-set (set @all-MIDs-atom)

          found-mixers
          (no-default (get-compatible-mixer-list DEFAULT_MIC_TARGET_DATA_LINE_INFO))

          found-MIDs
          (map mixer->MID found-mixers)

          ;current-MIDs-set
          ;(set @current-MIDs-atom)

          [added-MIDs-set removed-MIDs-set _]
          (data/diff (set found-MIDs) @current-MIDs-atom)
          ]
        (println " added-MIDs-set:" added-MIDs-set)
        (println "removed-MIDs-set:" removed-MIDs-set)

        (loop [MIDs added-MIDs-set]
            (when-let[MID (first MIDs)] ;; sentinel
                (println "new MID:" MID)
                (when-not (@all-MIDs-atom MID)
                    (println "Unseen! Adding to all-MIDs-atom")
                    (swap! all-MIDs-atom conj MID)
                    (println "    TODO: get line, open, add to lookup-map")
                    )
                (println "Adding to current-MIDs-atom")
                (swap! current-MIDs-atom conj MID)
                (recur (rest MIDs))))

        (when (seq removed-MIDs-set)
            (swap! current-MIDs-atom
                   (fn [clines]
                       (set (filter #(not (removed-MIDs-set %)) clines)))))

        ;; Update selected-MID-atom if necessary. If no MIDs, then nil
        (when-not (@current-MIDs-atom @selected-MID-atom)
            (reset! selected-MID-atom (first @current-MIDs-atom)))

        [added-MIDs-set removed-MIDs-set]))


(defn refresh-inputs-pane [monitors-vbox]
    (println "refresh-inputs-pane ...")

    (refresh-MIDs)

    (let [
          ;current-MIDs-set
          ;(set @current-MIDs-atom)

          monitors
          (-> monitors-vbox .getChildren)

          monitor-MIDs-set
          (set (map #(-> % .getUserData :MID) monitors))
          ]
        ;; add monitors for MIDs
        (loop [MIDs @all-MIDs-atom]
            (when-let[m (first MIDs)]
                ;(pprint MID)
                (when-not (monitor-MIDs-set m)
                    (println "adding monitor for MID:" m)
                    (fx/add monitors-vbox
                            (monitor-pane
                                m
                                (-> monitors-vbox .getUserData :togglegroup)
                                )))

                (recur (rest MIDs))))

        ;; activate/deactivate monitors based on current-MIDs
        (loop [monitors (-> monitors-vbox .getChildren)]
            (when-let [m (first monitors)]
                (let [
                      monitor-MID (-> m .getUserData :MID)
                      lights-pane (-> m .getUserData :lights-pane)
                      radiobutton (-> m .getUserData :radiobutton)
                      active (boolean (@current-MIDs-atom monitor-MID))
                      selected (= @selected-MID-atom monitor-MID)
                      ]
                    (.setVisible lights-pane active)
                    (.setSelected radiobutton selected)
                    )

                (recur (rest monitors))))
        ))





(defn- inputs-pane []
  (let [
        togglegroup
        (doto (ToggleGroup.)
            (-> .selectedToggleProperty
                (.addListener
                    (fx/changelistener
                        [_ _ _ monitor-radiobutton]
                        (let [MID (-> monitor-radiobutton .getParent .getUserData :MID)]
                            (println "selected MID:" MID)
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
        (fx/borderpane :top top :center monitors-vbox)
        ]

      [pane refresh-button-action]))








(defn create-inputs-stage []
  (fx/now
      (let [
            [pane refresh-button-action]
            (inputs-pane)

            stage
            (fx/stage
                :title "audio input selector"
                :scene (fx/scene pane)
                :sizetoscene true
                :onhidden #(del-singleton :inputs-stage)
                )
            ]
          (refresh-button-action)
          (.setUserData stage {:refresh-button-action refresh-button-action})
          stage)))


(defn inputs-stage []
  (singleton :inputs-stage create-inputs-stage))


;;
;(do (println "WARNING! Running george.audio.input/inputs-stage") (inputs-stage))