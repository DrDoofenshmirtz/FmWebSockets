(ns
  ^{:doc "Types for resource (lifecycle) management."
    :author "Frank Mosebach"}
  fm.websockets.resources.types
  (:use [fm.websockets.resources.operations :only (clean-up!)]))

(defprotocol ResourceStorage
  (update! [this update])
  (resources [this]))

(extend-protocol ResourceStorage
  clojure.lang.Ref
  (update! [this update]
    (clean-up! (dosync
                 (let [{:keys [good expired] :as resources} (update @this)]
                   (ref-set this (assoc resources :expired (empty expired)))
                   resources))))
  (resources [this]
    (:good @this)))
