(ns
  ^{:doc "Starts a WebSockets server that dispatches incoming JSON RPC requests
         to the 'file-upload-service' namespace."
    :author "Frank Mosebach"}
  fm.websockets.samples.fileupload.file-upload-app
  (:gen-class
    :name fm.websockets.samples.fileupload.FileUploadApp
    :main true)
  (:require
    [fm.websockets.resources :as rscs]
    [fm.websockets.json-rpc :as jrpc])
  (:use
    [clojure.contrib.logging :only (debug)]
    [clojure.contrib.command-line :only (with-command-line)]
    [fm.websockets.connection :only (ping)]
    [fm.websockets.server :only (start-up)]
    [fm.websockets.resources.storage :only (partition-storage)])
  (:import
    (java.util UUID)))

(def ^{:private true} service-namespace
                      'fm.websockets.samples.fileupload.file-upload-service)

(def ^{:private true} paritioned-storage (ref nil))

(defn- make-resource-storage [connection]
  (partition-storage paritioned-storage (str (UUID/randomUUID))))

(defn- make-connection-handler []
  (let [request-handler    (jrpc/ns-dispatcher service-namespace)
        request-handler    (rscs/request-handler request-handler)
        connection-handler (jrpc/connection-handler request-handler)
        connection-handler (rscs/connection-handler connection-handler
                                                    make-resource-storage)]
    (fn [connection]
      (ping connection)
      (connection-handler connection))))

(defn- close-resources []
  (println "Terminating application FileUploadApp...")
  (doseq [key (keys @paritioned-storage)]
    (println (format "Closing resources for %s..." key))
    (rscs/application-expired (partition-storage paritioned-storage key))
    (println "...closed."))
  (println "...done. Bye!"))

(defn- close-resources-on-shutdown []
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. close-resources "close-resources-on-shutdown")))

(defn -main [& args]
  (with-command-line args
    "FileUploadApp"
    [[port "The app's server port number" 17500]]
    (debug (format "Starting FileUploadApp server on port %s..." port))
    (close-resources-on-shutdown)
    (let [port (Integer/parseInt (.trim (str port)) 10)]
      (let [server (start-up port (make-connection-handler))]
        (debug "...done. Waiting for clients...")))))
