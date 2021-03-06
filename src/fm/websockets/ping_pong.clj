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
    (java.util.concurrent Executors TimeUnit)))

(def ^{:private true :const true} ping-scheduler 
                                  (Executors/newScheduledThreadPool 2))

(def ^{:private true :const true} ping-delay-seconds 30)

(defn- send-pong [connection pong-bytes]
  (let [pong-content (seq pong-bytes)]
    (log/debug (format "Sending pong: %s (connection: %s)..." 
                       pong-content 
                       (:id connection)))
    (try
      (conn/with-output-of connection
        (prot/send-pong % pong-bytes))
      (log/debug (format "...sent pong: %s (connection: %s)." 
                         pong-content 
                         (:id connection)))
      (catch Exception pong-error
        (when (conn/caused-by-closed-connection? pong-error)
          (throw pong-error))
        (log/error (format "Failed to send pong (connection: %s)!" 
                           (:id connection)) 
                           pong-error)))))

(defn- handle-ping-message [connection message]
  (log/debug (format "Received PING message %s (connection: %s)." 
                     (print-str message) 
                     (:id connection)))
  (send-pong connection (prot/message-payload message))
  connection)

(defn- handle-pong-message [connection message]
  (log/debug (format "Received PONG message %s (connection: %s)." 
                     (print-str message) 
                     (:id connection)))
  (let [pong-content (prot/message-content message)]
    (rsc/send-to! connection [::ping-pong] ::handle-pong pong-content))
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

(defn- handle-ping [{ping-backlog ::ping-backlog :as ping-pong} ping-content]
  (assoc ping-pong ::ping-content ping-content 
                   ::ping-backlog (inc ping-backlog)))

(defn- drop-ping [{ping-content ::ping-content :as ping-pong} pong-content]
  (when (= ping-content pong-content)
    (let [{ping-backlog ::ping-backlog} ping-pong]
      (-> ping-pong 
          (dissoc ::ping-content) 
          (assoc ::ping-backlog (max 0 (dec ping-backlog)))))))

(defn- handle-pong [{ping-content ::ping-content :as ping-pong} pong-content]
  (when (= ping-content pong-content)
    (-> ping-pong 
        (dissoc ::ping-content)
        (assoc ::ping-backlog 0))))

(defn- send-ping [connection]
  (let [ping-bytes   (prot/ping-bytes)
        ping-content (seq ping-bytes)]
    (rsc/send-to! connection [::ping-pong] ::handle-ping ping-content)
    (log/debug (format "Sending ping: %s (connection: %s)..." 
                       ping-content 
                       (:id connection)))
    (try
      (conn/with-output-of connection
        (prot/send-ping % ping-bytes))
      (log/debug (format "...sent ping: %s (connection: %s)." 
                         ping-content 
                         (:id connection)))
      (catch Exception ping-error
        (when-not (conn/caused-by-closed-connection? ping-error)
          (rsc/send-to! connection [::ping-pong] ::drop-ping ping-content)
          (log/error (format "Failed to send ping (connection: %s)!" 
                             (:id connection)) 
                     ping-error))))))

(def ^{:private true :const true} ping-pong-context :connection)

(defn- ping-backlog [connection]
  (-> connection 
      (rsc/get-resource ping-pong-context ::ping-pong) 
      (::ping-backlog 0)))

(defn- ping-task [connection start-gate max-backlog]
  (fn []
    (deref start-gate)
    (when (< (ping-backlog connection) max-backlog)
      (send-ping connection))))

(defn- schedule-ping-task [connection start-gate max-backlog]
  (.scheduleWithFixedDelay ping-scheduler 
                           (ping-task connection start-gate max-backlog)  
                           ping-delay-seconds 
                           ping-delay-seconds 
                           TimeUnit/SECONDS))

(defn- store-ping-pong [connection max-backlog]
  (let [start-gate (promise)
        ping-task  (schedule-ping-task connection start-gate max-backlog)
        ping-pong  {::ping-task ping-task ::ping-backlog 0}
        close!     cancel-ping-task!
        slots      {::handle-ping handle-ping
                    ::drop-ping   drop-ping
                    ::handle-pong handle-pong}]
    (rsc/store! connection ping-pong-context 
                ::ping-pong ping-pong 
                :connection
                :close! close!
                :slots  slots)
    (deliver start-gate nil)))

(defn connection-handler
  ([]
    (connection-handler 2))
  ([max-backlog]
    (let [max-backlog (max max-backlog 1)]
      (fn [connection]
        (store-ping-pong (conn/drop-messages connection) max-backlog)
        connection))))

