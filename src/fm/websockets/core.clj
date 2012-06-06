(ns fm.websockets.core
  (:use
    [fm.websockets.server :only (start-up)]
    [fm.websockets.json-rpc :only (connection-handler)]))

(defn- print-rpc [connection method params]
  (println (format "RPC{ method: %s, params: %s }" method params)))

(defn- make-connection-handler []
  (let [json-rpc-handler (connection-handler print-rpc)]
    (fn [connection]
      (println "Connection established!")
      (let [connection (json-rpc-handler connection)]
        (println (format "Connection %s closed. Bye!" connection))
        connection))))

(start-up 17500 (make-connection-handler))
