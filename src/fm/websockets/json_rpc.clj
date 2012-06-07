(ns
  ^{:doc "JSON RPC layer for HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.json-rpc
  (:use
    [clojure.contrib.json :only (json-str read-json)]
    [fm.core.bytes :only (signed-byte)]
    [fm.core.threading :only (with-guarded)]
    [fm.websockets.protocol :only (text-message?
                                   send-text-message
                                   message-content)]
    [fm.websockets.connection :only (take-message)])
  (:import
    (java.util UUID)))

(defn send-object [output-stream object]
  (send-text-message output-stream (json-str object)))

(defn send-response [output id result]
  (with-guarded output
    (send-object % {:id id :result result :error nil})))

(defn send-error [output id error]
  (with-guarded output
    (send-object % {:id id :result nil :error error})))

(defn send-notification [output method & params]
  (with-guarded output
    (send-object % {:id nil :method method :params params})))

(defn- sign-connection [connection]
  (assoc connection :id (str (UUID/randomUUID))))

(defn- acknowledge-connection [{:keys [id output] :as connection}]
  (send-notification output "connectionAcknowledged" id)
  connection)

(defn- maybe-rpc-message? [message]
  (and message (text-message? message)))

(defn- try-read-rpc [message]
  (try
    (read-json (message-content message))
    (catch Exception _ nil)))

(defn- read-rpc [message]
  (if (maybe-rpc-message? message)
    (try-read-rpc message)))

(defn- dispatch-rpc [connection rpc-dispatcher message]
  (if-let [{:keys [id method params]} (read-rpc message)]
    (rpc-dispatcher connection method params)
    connection))

(defn- dispatch-rpcs [connection rpc-dispatcher]
  (loop [[message connection] (take-message connection)]
    (if message
      (recur (take-message (dispatch-rpc connection rpc-dispatcher message)))
      connection)))

(defn connection-handler [rpc-dispatcher]
  (fn [connection]
    (let [connection (-> connection sign-connection acknowledge-connection)]
      (dispatch-rpcs connection rpc-dispatcher))))
