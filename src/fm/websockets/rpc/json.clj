(ns
  ^{:doc "Support for JSON as an RPC format."
    :author "Frank Mosebach"}
  fm.websockets.rpc.json
  (:require
    [clojure.contrib.json :as json]
    [fm.websockets.protocol :as prot]
    [fm.websockets.rpc.core :as rpc]
    [fm.websockets.rpc.format :as fmt]))

(fmt/declare-format json)

(defn connection-handler []
  (rpc/connection-handler rpc-format))

(defmethod fmt/message->request rpc-format [_ message]
  (when (and message (prot/text-message? message))
    (json/read-json (prot/message-content message))))

(defmethod fmt/result->content rpc-format [_ id result]
  (json/json-str {:id id :result (if (nil? result) true result) :error nil}))

(defmethod fmt/error->content rpc-format [_ id error]
  (json/json-str {:id id :result nil :error (if (nil? error) true error)}))

(defmethod fmt/request->content rpc-format [_ id name args]
  (json/json-str {:id id :method name :params args}))

(defmethod fmt/notification->content rpc-format [_ name args]
  (json/json-str {:id nil :method name :params args}))

