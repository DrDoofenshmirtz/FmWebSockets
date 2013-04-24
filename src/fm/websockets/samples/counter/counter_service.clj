(ns
  ^{:doc "A demo namespace to be used with a JSON namespace RPC dispatcher."
    :author "Frank Mosebach"}
  fm.websockets.samples.counter.counter-service
  (:require 
    [fm.websockets.rpc.request :as req]))

(defn reset-counter [value]
  (req/alter-connection! assoc :counter value)
  value)

(defn inc-counter [increment]
  (let [value (+ (:counter (req/connection)) increment)]
    (req/alter-connection! assoc :counter value)
    value))

