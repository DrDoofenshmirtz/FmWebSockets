(ns
  ^{:doc "Types for resource (lifecycle) management."
    :author "Frank Mosebach"}
  fm.websockets.resources.types)

(defprotocol ResourceStorage
  "Defines the contract for a place where resources can be stored."
  (update! [this update]
    "Updates the currently stored resources through application of
    the given update function.")
  (contents [this]
    "Returns (a snapshot of) the currenly stored contents."))
