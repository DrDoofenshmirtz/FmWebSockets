(ns
  ^{:doc "Starts a WebSockets server that dispatches incoming JSON RPC requests
         to the 'counter-service' namespace."
    :author "Frank Mosebach"}
  fm.websockets.samples.counter.counter-app
  (:gen-class
    :name fm.websockets.samples.counter.CounterApp
    :main true)
  (:use
    [clojure.contrib.command-line :only (with-command-line)]
    [fm.websockets.server :only (start-up)]
    [fm.websockets.json-rpc :only (connection-handler ns-dispatcher)]))

(defn- print-request [connection method params]
  (println (format "Request{ method: %s, params: %s }" method params)))

(defn- request-dispatcher []
  (let [ns-dispatcher (ns-dispatcher 'fm.websockets.samples.counter.counter-service)]
    (fn [connection method params]
      (print-request connection method params)
      (ns-dispatcher connection method params))))

(defn- make-connection-handler []
  (let [connection-handler (connection-handler (request-dispatcher))]
    (fn [connection]
      (println "Connection established!")
      (let [connection (assoc connection :counter 0)
            connection (connection-handler connection)]
        (println (format "Connection %s closed. Bye!" connection))
        connection))))

(defn -main [& args]
  (with-command-line args
    "CounterApp"
    [[port "The app's server port number" 17500]]
    (println (str "Starting CounterApp server on port " port "..."))
    (let [port (Integer/parseInt (.trim (str port)) 10)]
      (start-up port (make-connection-handler)))))
