(ns
  ^{:doc "Types for resource (lifecycle) management."
    :author "Frank Mosebach"}
  fm.websockets.resources.types
  (:require
    [fm.websockets.resources.operations :as ops]))

(defprotocol ResourceStorage
  "Defines the contract for a place where resources can be stored."
  (update! [this update]
    "Updates the currently stored resources through application of
    the given update function.")
  (resource [this key]
    "Returns the resource that is stored under the given key.")
  (resources [this]
    "Returns (a snapshot of) the currenly stored resources."))

(extend-protocol ResourceStorage
  clojure.lang.Ref
  (update! [this update]
    (dosync
      (let [{good :good :as resources} (update {:good @this})]
        (ref-set this good)
        resources)))
  (resource [this key]
    (:resource (get @this key)))
  (resources [this]
    (ops/resources {:good @this})))
