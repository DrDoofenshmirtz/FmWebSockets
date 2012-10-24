(ns
  ^{:doc "API for resource access."
    :author "Frank Mosebach"}
  fm.websockets.resources.access
  (:use
    [fm.websockets.resources.operations :only (manage update)]
    [fm.websockets.resources.types :only (update! resources)])
  (:import
    (fm.websockets.resources.types ResourceStorage)))

(defn manage! [storage key resource & more]
  (update! storage #(apply manage % key resource more)))
