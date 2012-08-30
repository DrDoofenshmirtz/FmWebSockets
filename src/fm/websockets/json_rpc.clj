(ns
  ^{:doc "JSON RPC layer for HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.json-rpc
  (:refer-clojure :exclude [send])
  (:use
    [clojure.contrib.logging :only (debug fatal)]
    [clojure.contrib.json :only (json-str read-json)]
    [fm.core.bytes :only (signed-byte)]
    [fm.core.hyphenate :only (hyphenate)]
    [fm.websockets.protocol :only (text-message? message-content)]
    [fm.websockets.connection :only (send take-message)])
  (:import
    (java.util UUID)))

(defn send-object [target object]
  (send target (json-str object)))

(defn send-response [target id result]
  (send-object target {:id id :result result :error nil}))

(defn send-error [target id error]
  (send-object target {:id id :result nil :error error}))

(defn send-notification [target method & params]
  (send-object target {:id nil :method method :params params}))

(defn- sign-connection [connection]
  (assoc connection :id (str (UUID/randomUUID))))

(defn- acknowledge-connection [{:keys [id] :as connection}]
  (send-notification connection "connectionAcknowledged" id)
  connection)

(defn- maybe-request? [message]
  (and message (text-message? message)))

(defn- try-read-request [message]
  (try
    (read-json (message-content message))
    (catch Exception _ nil)))

(defn- read-request [message]
  (if (maybe-request? message)
    (try-read-request message)))

(defn- check-request-id [connection-id request-id]
  (if-not (.startsWith (str request-id) (str connection-id))
    (throw (IllegalArgumentException.
             (format "Illegal request id: '%s'!" request-id)))))

(defn- result-type [connection & args]
  (if (nil? connection)
    (throw (IllegalArgumentException. "Illegal connection: nil!"))
    (type (first args))))

(defmulti result result-type)

(defmethod result ::result [connection & args]
  (first args))

(defmethod result Throwable [connection & args]
  (let [[throwable error?] args]
    (result
      connection
      {:error (-> throwable class .getName)
       :message (.getMessage throwable)}
      error?)))

(defmethod result nil [connection & args]
  (let [[return-value error?] args]
    (result connection (if error? false true) error?)))

(defmethod result :default [connection & args]
  (let [[return-value error?] args]
    (with-meta
      [connection return-value (if error? true false)]
      {:type ::result})))

(defn- response [id result]
  (let [[connection return-value error?] result
        response (if error?
                   {:result nil :error return-value}
                   {:result return-value :error nil})]
    (with-meta (assoc response :id id) {:type ::response})))

(defn- dispatch-request [connection request-dispatcher message]
  (if-let [{:keys [id method params]} (read-request message)]
    (do
      (debug (format
               "Dispatch request {id: %s, method: %s, params: %s}..."
               id method params))
      (check-request-id (:id connection) id)
      (let [result (try
                     (result
                       connection
                       (request-dispatcher connection method params))
                     (catch Throwable error (result connection error true)))
            [connection return-value] result]
        (debug (format "...done. Return value: %s." return-value))
        (debug "Send response...")
        (send-object connection (response id result))
        (debug "...done.")
        connection))
    (do
      (debug "Skipped message.")
      connection)))

(defn- run-dispatch-loop [request-dispatcher [message connection]]
  (if message
    (recur
      request-dispatcher
      (take-message
        (dispatch-request
          connection
          request-dispatcher
          message)))
    connection))

(defn- dispatch-requests [connection request-dispatcher]
  ;; IMPORTANT: The dispatch loop is executed in a separate function that
  ;;            invokes itself recursively. Using a "loop"-form does not
  ;;            work here, because the initial binding keeps a reference
  ;;            to the connection with all messages but the first, preventing
  ;;            old messages from being gc'ed properly!
  (run-dispatch-loop request-dispatcher (take-message connection)))

(defn connection-handler [request-dispatcher]
  (fn [connection]
    (debug "JSON RPC connection established.")
    (debug (format "Request: %s" (:request connection)))
    (debug "Dispatching requests...")
    (let [connection (-> connection
                         sign-connection
                         acknowledge-connection
                         (dispatch-requests request-dispatcher))]
      (debug (format "JSON RPC connection closed: %s." connection)))))

(defn ns-dispatcher [ns-name]
  (require ns-name)
  (fn [connection method params]
    (let [procedure-name (symbol (hyphenate method))]
      (if-let [procedure ((ns-interns ns-name) procedure-name)]
        (apply procedure connection params)
        (let [error-message (format
                              "Undefined procedure: '%s' in namespace '%s'!"
                              procedure-name ns-name)]
          (fatal error-message)
          (throw (IllegalArgumentException. error-message)))))))
