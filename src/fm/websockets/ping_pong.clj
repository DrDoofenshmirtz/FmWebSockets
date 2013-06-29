(ns
  ^{:doc 
  
  "Processing and emission of ping messages."
  
    :author "Frank Mosebach"}
  fm.websockets.ping-pong
  (:require
    [clojure.contrib.logging :as log]
    [fm.websockets.protocol :as prot]
    [fm.websockets.connection :as conn]
    [fm.websockets.resources :as rsc])
  (:import
    (java.util UUID)
    (java.util.concurrent Executors)))

(def ^{:private true} ping-scheduler (Executors/newScheduledThreadPool 2))

(defn- handle-ping-message [connection message]
  connection)

(defn- handle-pong-message [connection message]
  connection)

(defn message-handler [message-handler]
  (assert message-handler)
  (fn [connection message]
    (cond
      (prot/ping? message) (handle-ping-message connection message)
      (prot/pong? message) (handle-pong-message connection message)
      :else (message-handler connection message))))

(defn- cancel-ping-task! [{ping-task ::ping-task}]
  (when ping-task
    (.cancel ping-task true))
  nil)

(defn- handle-pong [ping-pong pong-bytes])

(defn- schedule-ping-task [stripped-connection start-gate])

(defn- store-ping-pong [stripped-connection]
  (let [start-gate (promise)
        ping-task  (schedule-ping-task stripped-connection start-gate)
        ping-pong  {::ping-task ping-task ::ping-backlog 0}
        close!     cancel-ping-task!
        slots      {::handle-pong handle-pong}]
    (rsc/store! stripped-connection 
                ::ping-pong ping-pong 
                :connection
                :close! close!
                :slots  slots)))

(defn connection-handler []
  (fn [connection]
    (store-ping-pong (conn/drop-messages connection))
    connection))

