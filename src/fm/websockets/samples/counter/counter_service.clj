(ns
  ^{:doc "A demo namespace to be used with a JSON namespace RPC dispatcher."
    :author "Frank Mosebach"}
  fm.websockets.samples.counter.counter-service)

(defn reset-counter [connection value]
  (reset! (:counter connection) value))

(defn inc-counter [connection increment]
  (swap! (:counter connection) + increment))
