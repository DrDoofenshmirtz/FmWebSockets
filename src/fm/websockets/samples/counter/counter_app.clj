(ns
  ^{:doc "Starts a WebSockets server that dispatches incoming JSON RPC requests
         to the 'counter-service' namespace."
    :author "Frank Mosebach"}
  fm.websockets.samples.counter.counter-app
  (:gen-class
    :name fm.websockets.samples.counter.CounterApp
    :main true)
  (:use
    [clojure.contrib.def :only (defvar-)]
    [clojure.contrib.logging :only (debug)]
    [clojure.contrib.command-line :only (with-command-line)]
    [fm.websockets.server :only (start-up)]
    [fm.websockets.json-rpc :only (connection-handler ns-dispatcher)]))

(defvar- service-namespace 'fm.websockets.samples.counter.counter-service)

(defn- make-connection-handler []
  (let [connection-handler (connection-handler
                             (ns-dispatcher service-namespace))]
    (fn [connection]
      (connection-handler (assoc connection :counter 0)))))

(defn -main [& args]
  (with-command-line args
    "CounterApp"
    [[port "The app's server port number" 17500]]
    (debug (format "Starting CounterApp server on port %s..." port))
    (let [port (Integer/parseInt (.trim (str port)) 10)]
      (start-up port (make-connection-handler))
      (debug "...done. Waiting for clients..."))))
