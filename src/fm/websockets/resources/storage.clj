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

(defn manage! [storage key resource & kwargs]
  (-> (types/update! storage #(apply ops/manage % key resource kwargs))
      ops/clean-up!
      :good))

(defn update! [storage & kwargs]
  (-> (types/update! storage #(apply ops/update % kwargs))
      ops/clean-up!
      :good))

(defn send! [storage id event & keys]
  (-> (types/update! storage #(ops/send-event % :id    id
                                                :event event
                                                :keys  keys))
      ops/clean-up!
      :good))

(defn remove! [storage key & keys]
  (-> (types/update! storage #(ops/remove % (cons key keys)))
      ops/clean-up!
      :good))

(defn sweep! [storage]
  (update! storage :update identity))

(defn clear! [storage]
  (-> (types/update! storage #(ops/remove %))
      ops/clean-up!
      :good))

(defn resource
  ([storage key]
    (resource storage key nil))
  ([storage key default]
    (if-let [{resource :resource} (get (types/contents storage) key)]
      resource
      default)))
