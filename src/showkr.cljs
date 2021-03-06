(ns showkr
  (:require [cljsjs.react]
            [cljs.reader :refer [read-string]]
            [quiescent :as q]
            [quiescent.dom :as d]
            [datascript :as db]

            [showkr.data :as data]
            [showkr.utils :refer [get-route]]
            [showkr.root :refer [Root]]))

(enable-console-print!)

(def ^:private render-queued false)
(defn ^:private actually-render []
  (set! render-queued false)
  (when (:debug @data/opts)
    (js/console.time "render"))
  (q/render (Root {:opts @data/opts :db @data/db}
              #(swap! data/opts update-in [:debug] not))
    (.getElementById js/document (:target @data/opts)))
  (when (:debug @data/opts)
    (js/console.timeEnd "render")))

(defn render []
  (when-not render-queued
    (set! render-queued true)
    (if (exists? js/requestAnimationFrame)
      (js/requestAnimationFrame actually-render)
      (js/setTimeout actually-render 16))))

(defn trigger-render []
  (swap! data/opts update :dev-reload not))

(defn ^:export main [id opts]
  (let [opts (js->clj opts :keywordize-keys true)]

    ;; listen for data changes
    (add-watch data/opts ::render render)
    (add-watch data/db ::render render)

    (add-watch data/db ::store
      (fn []
        (.setItem js/window.localStorage "db" (pr-str @data/db))))

    ;; listen for path changes
    (.addEventListener js/window "hashchange"
      #(let [path (get-route)
             path (if (empty? path) (:path opts "") path)]
         (swap! data/opts assoc :path path)))

    ;; kick off rendering
    (when-let [stored (.getItem js/window.localStorage "db")]
      (reset! data/db (read-string stored)))

    (swap! data/opts merge
      {:target id :path (get-route)}
      (or opts {}))))
