(ns
  ^{:doc "API for resource access."
    :author "Frank Mosebach"}
  fm.websockets.resources.access
  (:use
    [fm.websockets.resources.operations :only (manage clean-up! resources)]
    [fm.websockets.resources.types :only (update!)]))

(defn manage! [storage key resource & more]
  (-> (update! storage #(apply manage % key resource more))
      clean-up!
      resources))
