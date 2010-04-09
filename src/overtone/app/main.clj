(ns overtone.app.main
  (:gen-class)
  (:import 
    (java.awt Toolkit EventQueue Dimension Point Dimension Color Font 
              RenderingHints Point BasicStroke BorderLayout)
    (java.awt.event WindowAdapter)
    (java.awt.geom Ellipse2D$Float RoundRectangle2D$Float)
    (javax.swing JFrame JPanel JSplitPane JLabel JButton BorderFactory
                 JSpinner SpinnerNumberModel UIManager BoxLayout) 
    (com.sun.scenario.scenegraph JSGPanel SGText SGShape SGGroup 
                                 SGAbstractShape$Mode SGComponent SGTransform)
    (com.sun.scenario.scenegraph.event SGMouseAdapter)
    (com.sun.scenario.scenegraph.fx FXShape))
  (:use (overtone.app editor tools)
        (overtone.core sc ugen synth envelope event time-utils)
        (overtone.gui swing sg scope curve repl)
        clojure.stacktrace
        (clojure.contrib
          [miglayout :only (miglayout components)]))
  (:require [overtone.core.log :as log]))

(alias 'ug 'overtone.ugens)

(def app* (ref {:name "Overtone"
                :padding 5.0
                :background (Color. 50 50 50)
                :foreground (Color. 255 255 255)
                :header-fg (Color. 255 255 255)
                :header-font (Font. "helvetica" Font/BOLD 16)
                :header-height 20
                :status-update-period 1000
                :edit-font (Font. "helvetica" Font/PLAIN 12)
                :edit-panel-dim (Dimension. 550 900)
                :scene-panel-dim (Dimension. 615 900)
                :tools-panel-dim (Dimension. 300 900)
                }))

(defn metro-panel []
  (let [bpm-lbl (JLabel. "BPM: ")
        bpm-model (SpinnerNumberModel. 120 1 400 1)
        bpm-spin (JSpinner. bpm-model)
        beat-panel (JPanel.)]
    (.setForeground bpm-lbl (:header-fg @app*))
    (doto beat-panel
      (.setBackground (:background @app*))
      (.add bpm-lbl)
      (.add bpm-spin))))

(defn status-panel []
  (let [ugen-lbl  (JLabel. "UGens: 0")
        synth-lbl (JLabel. "Synths: 0")
        group-lbl (JLabel. "Groups: 0")
        cpu-lbl   (JLabel. "CPU: 0.00")
        border (BorderFactory/createEmptyBorder 2 5 2 5)
        lbl-panel (JPanel.)
        updater (fn [] (let [sts (status)]
                           (in-swing
                             (.setText ugen-lbl  (format "ugens: %4d" (:n-ugens sts)))
                             (.setText synth-lbl (format "synths: %4d" (:n-synths sts)))
                             (.setText group-lbl (format "groups: %4d" (:n-groups sts)))
                             (.setText cpu-lbl   (format "avg-cpu: %4.2f" (:avg-cpu sts))))))]
    (doto lbl-panel
      (.setBackground (:background @app*))
      (.add ugen-lbl )
      (.add synth-lbl)
      (.add group-lbl)
      (.add cpu-lbl))

    (doseq [lbl [ugen-lbl synth-lbl group-lbl cpu-lbl]]
      (.setBorder lbl border)
      (.setForeground lbl (:header-fg @app*)))

    (on :connected #(periodic updater (:status-update-period @app*)))
    lbl-panel))

(defn header []
  (let [panel (JPanel.)
        metro (metro-panel)
        status (status-panel)
        boot-btn (JButton. "Boot")
        help-btn (JButton. "Help")
        quit-btn (JButton. "Quit")
        btn-panel (JPanel.)]

    (on-action boot-btn #(boot))
    (on-action help-btn #(println "help is on the way!"))
    (on-action quit-btn #(do (quit) (System/exit 0)))

    (doto btn-panel
      (.setBackground (:background @app*))
      (.add boot-btn)
      (.add help-btn)
      (.add quit-btn))

    (doto panel
      (.setLayout (BorderLayout.))
      (.setBackground (:background @app*))
      (.add metro BorderLayout/WEST)
      (.add status BorderLayout/CENTER)
      (.add btn-panel BorderLayout/EAST))

    (dosync (alter app* assoc :header panel))
    panel))

(defn controls-scene []
  (let [root (sg-group)]
    (doto root
      (add! (translate (:padding @app*) 0.0 (curve-editor))))
    (dosync (alter app* assoc :scene-group root))
    root))

(defn window-listener [frame]
  (proxy [WindowAdapter] []
    (windowClosed [win-event] (try (.setVisible frame false) (quit) (finally (System/exit 0))))
    (windowIconified [win-event] (event :iconified))
    (windowDeiconified [win-event] (event :deiconified))
    ))

(defn overtone-frame []
  (let [app-frame (JFrame.  "Project Overtone")
        app-panel (.getContentPane app-frame)
        ;browse-panel (browser)
        header-panel (header)
        edit-panel (editor-panel @app*)
        repl-panel (repl-panel)
        hack-panel (JSplitPane. JSplitPane/VERTICAL_SPLIT edit-panel repl-panel)
        scene-panel (JSGPanel.)
        scope-panel (scope-panel)
        g-panel (JPanel.)
        tool-panel (tools-panel @app*)]
        ;left-split (JSplitPane. JSplitPane/HORIZONTAL_SPLIT browse-panel hack-panel)]

    ;(when (not (connected?))
    ;  (boot)
    ;  (Thread/sleep 1000))

    (doto edit-panel
      (.setPreferredSize (:edit-panel-dim @curve*)))

    (doto scene-panel
      (.setBackground Color/BLACK)
      (.setScene (controls-scene))
      (.setMinimumSize (Dimension. 600 400)))
;      (.setPreferredSize (:scene-panel-dim @curve*)))

    (doto tool-panel
      (.setPreferredSize (:tools-panel-dim @curve*)))

    (doto repl-panel
      (.setMinimumSize (Dimension. 400 400)))

    (doto g-panel
      (.setLayout (BorderLayout.))
      (.add scope-panel BorderLayout/NORTH)
      (.add scene-panel BorderLayout/SOUTH))

    (miglayout app-panel
      header-panel "dock north"
      hack-panel "cell 0 1 1 2"
      scene-panel "cell 1 1"
      scope-panel "cell 1 2"
      tool-panel "dock east")

    ;(.setDividerLocation left-split 0.4)

    (doto app-frame
      (.addWindowListener (window-listener app-frame))
      (.pack)
      (.setVisible true))))

(defn -main [& args]
  (let [system-lf (UIManager/getSystemLookAndFeelClassName)]
    ; Maybe we need Java7 for this API?
    ;(if-let [screen (GraphicsEnvironment/getDefaultScreenDevice)]
    ;  (if (.isFullScreenSupported screen) 
    ;    (.setFullScreenWindow screen window))
    (UIManager/setLookAndFeel system-lf)
    (in-swing (overtone-frame))))
