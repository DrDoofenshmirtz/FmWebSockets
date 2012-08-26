(ns
  ^{:doc "A demo namespace to be used with a JSON namespace RPC dispatcher."
    :author "Frank Mosebach"}
  fm.websockets.samples.counter.counter-service
  (:use [fm.websockets.json-rpc :only (result)]))

(defn reset-counter [connection value]
  (result (assoc connection :counter value) value))

(defn inc-counter [connection increment]
  (let [value (+ (:counter connection) increment)]
    (result (assoc connection :counter value) value)))
