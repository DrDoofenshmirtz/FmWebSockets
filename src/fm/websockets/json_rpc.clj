(ns
  ^{:doc "JSON RPC layer for HTML5 WebSocket connections."
    :author "Frank Mosebach"}
  fm.websockets.json-rpc
  (:use
    [clojure.contrib.json :only (json-str read-json)]
    [fm.core.bytes :only (signed-byte)]
    [fm.core.threading :only (with-guarded)]
    [fm.core.hyphenate :only (hyphenate)]
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

(defn- maybe-request? [message]
  (and message (text-message? message)))

(defn- try-read-request [message]
  (try
    (read-json (message-content message))
    (catch Exception _ nil)))

(defn- read-request [message]
  (if (maybe-request? message)
    (try-read-request message)))

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
    (let [result (try
                   (result
                     connection
                     (request-dispatcher connection method params))
                   (catch Throwable error (result connection error true)))
          [connection] result]
      (with-guarded (:output connection)
        (send-object % (response id result)))
      connection)
    connection))

(defn- dispatch-requests [connection request-dispatcher]
  (loop [[message connection] (take-message connection)]
    (if message
      (recur (take-message
               (dispatch-request
                 connection
                 request-dispatcher
                 message)))
      connection)))

(defn connection-handler [request-dispatcher]
  (fn [connection]
    (let [connection (-> connection sign-connection acknowledge-connection)]
      (dispatch-requests connection request-dispatcher))))

(defn ns-dispatcher [ns-name]
  (require ns-name)
  (fn [connection method params]
    (let [procedure-name (symbol (hyphenate method))]
      (if-let [procedure ((ns-interns ns-name) procedure-name)]
        (apply procedure connection params)
        (throw (IllegalArgumentException.
                 (format
                   "Undefined procedure: '%s' in namespace '%s'!"
                   procedure-name ns-name)))))))
