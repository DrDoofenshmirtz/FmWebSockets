(ns fm.websockets.core
  (:use
    [fm.websockets.server :only (start-up)]
    [fm.websockets.json-rpc :only (connection-handler ns-dispatcher)]))

(defn- print-request [connection method params]
  (println (format "Request{ method: %s, params: %s }" method params)))

(defn- request-dispatcher []
  (let [ns-dispatcher (ns-dispatcher 'fm.websockets.rpc-demo)]
  (fn [connection method params]
    (print-request connection method params)
    (ns-dispatcher connection method params))))

(defn- make-connection-handler []
  (let [connection-handler (connection-handler (request-dispatcher))]
    (fn [connection]
      (println "Connection established!")
      (let [connection (connection-handler connection)]
        (println (format "Connection %s closed. Bye!" connection))
        connection))))

(start-up 17500 (make-connection-handler))
