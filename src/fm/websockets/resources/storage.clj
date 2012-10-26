(ns
  ^{:doc "API for resource storage access and manipulation."
    :author "Frank Mosebach"}
  fm.websockets.resources.storage
  (:use
    [fm.websockets.resources.operations :only (manage clean-up!)]
    [fm.websockets.resources.types :only (ResourceStorage update! contents)]))

(defn ref-storage []
  (let [storage (ref nil)]
    (reify ResourceStorage
      (update! [this update]
        (dosync
          (let [{good :good :as resources} (update {:good @storage})]
            (ref-set storage good)
            resources)))
      (contents [this]
        @storage))))

(defn manage! [storage key resource & more]
  (-> (update! storage #(apply manage % key resource more))
      clean-up!
      :good))

(defn resources [storage & kees]
  (let [contents (contents storage)
        kees     (or (seq kees) (keys contents))]
    (map #(:resource (get contents %)) kees)))

