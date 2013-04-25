(ns
  ^{:doc "Starts a WebSockets server that dispatches incoming JSON RPC requests
         to the 'file-upload-service' namespace."
    :author "Frank Mosebach"}
  fm.websockets.samples.fileupload.file-upload-app
  (:gen-class
    :name fm.websockets.samples.fileupload.FileUploadApp
    :main true)
  (:require    
    [clojure.contrib.logging :as log]
    [clojure.contrib.command-line :as cli]
    [fm.resources.store :as rstore]
    [fm.websockets.resources :as rscs]
    [fm.websockets.rpc.core :as rpc]
    [fm.websockets.rpc.request :as req]
    [fm.websockets.rpc.json :as jrpc]
    [fm.websockets.message-loop :as mloop]
    [fm.websockets.connection :as conn]
    [fm.websockets.server :as server]))

(def ^{:private true} 
     service-namespace 'fm.websockets.samples.fileupload.file-upload-service)

(def ^{:private true} resource-store (ref nil))

(def ^{:private true} request-handler (-> service-namespace
                                          req/ns-router
                                          rscs/request-handler))

(def ^{:private true} message-handler (-> request-handler
                                          rpc/message-handler))

(defn- store-constructor [connection]
  (rstore/partition-store resource-store (:id connection)))

(def ^{:private true} 
     connection-handler (-> (comp (mloop/connection-handler message-handler) 
                                  (jrpc/connection-handler)
                                  (fn [connection]
                                    (conn/ping connection)
                                    connection))
                            (rscs/connection-handler store-constructor)))

(defn- close-resources []
  (println "Terminating application FileUploadApp...")
  (doseq [key (keys @resource-store)]
    (println (format "Closing resources for %s..." key))
    (rscs/application-expired! (rstore/partition-store resource-store key))
    (println "...closed."))
  (println "...done. Bye!"))

(defn- close-resources-on-shutdown []
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. close-resources "close-resources-on-shutdown")))

(defn run [& args]
  (cli/with-command-line args
    "FileUploadApp"
    [[port "The app's server port number" 17500]]
    (log/debug (format "Starting FileUploadApp server on port %s..." port))
    (close-resources-on-shutdown)
    (let [port (Integer/parseInt (.trim (str port)) 10)]
      (let [server (server/start-up port connection-handler)]
        (log/debug "...done. Waiting for clients...")))))

(defn -main [& args]
  (apply run args))

