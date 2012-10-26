(ns
  ^{:doc "API for resource storage access and manipulation."
    :author "Frank Mosebach"}
  fm.websockets.resources.storage
  (:require
    [fm.websockets.resources.operations :as ops]
    [fm.websockets.resources.types :as types]))

(defn ref-storage []
  (let [storage (ref nil)]
    (reify types/ResourceStorage
      (update! [this update]
        (dosync
          (let [{good :good :as resources} (update {:good @storage})]
            (ref-set storage good)
            resources)))
      (contents [this]
        @storage))))

(defn manage! [storage key resource & more]
  (-> (types/update! storage #(apply ops/manage % key resource more))
      ops/clean-up!
      :good))

(defn resource
  ([storage key]
    (resource storage key nil))
  ([storage key default]
    (if-let [{resource :resource} (get (types/contents storage) key)]
      resource
      default)))
