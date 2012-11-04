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

(defn partition-storage [ref key]
  (reify types/ResourceStorage
    (update! [this update]
      (dosync
        (let [{good :good :as resources} (update {:good (get @ref key)})]
          (if (empty? good)
            (alter ref dissoc key)
            (alter ref assoc key good))
          resources)))
    (contents [this]
      (get @ref key))))

(defn- update-and-clean-up [storage update]
  (-> (types/update! storage update) ops/clean-up! :good))

(defn manage! [storage key resource & kwargs]
  (update-and-clean-up storage #(apply ops/manage % key resource kwargs)))

(defn update! [storage & kwargs]
  (update-and-clean-up storage #(apply ops/update % kwargs)))

(defn send! [storage id event & keys]
  (update-and-clean-up storage
                       #(ops/send-event % :id id :event event :keys keys)))

(defn remove! [storage key & keys]
  (update-and-clean-up storage #(ops/remove % (cons key keys))))

(defn sweep! [storage]
  (update! storage :update identity))

(defn clear! [storage]
  (update-and-clean-up storage #(ops/remove %)))

(defn resource
  ([storage key]
    (resource storage key nil))
  ([storage key default]
    (if-let [{resource :resource} (get (types/contents storage) key)]
      resource
      default)))
